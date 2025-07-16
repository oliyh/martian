(ns martian.server-stub
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.walk :as w]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [pedestal-api
             [core :as api]
             [helpers :refer [before defbefore defhandler handler]]]
            [ring.util.codec :as codec]
            [schema.core :as s])
  (:import (java.io File)
           (java.nio.file Path)))

(defonce the-pets (atom {}))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(defhandler create-pet
  {:summary    "Create a pet"
   :parameters {:body-params Pet}
   :responses  {201 {:body {:id s/Int}}}}
  [request]
  (let [id 123]
    (swap! the-pets assoc id (:body-params request))
    {:status 201
     :body {:id id}}))

(defhandler get-pet
  {:summary    "Get a pet by id"
   :parameters {:path-params {:id s/Int}}
   :responses  {200 {:body Pet}
                404 {:body s/Str}}}
  [request]
  (if-let [pet (get @the-pets (get-in request [:path-params :id]))]
    {:status 200
     :body pet}
    {:status 404
     :body "No pet found with this id"}))

(s/defschema Upload
  {(s/optional-key :string) s/Str
   (s/optional-key :binary) s/Any
   (s/optional-key :custom) s/Any})

(defn- get-content-type-header [request]
  (or (get-in request [:headers "content-type"])
      (get-in request [:headers "Content-Type"])
      (get-in request [:headers :content-type])))

(defn- get-prepared-content-map [request]
  (w/postwalk (fn [obj]
                (cond
                  (instance? File obj) (slurp obj)
                  (instance? Path obj) (slurp (.toFile obj))
                  (= 'org.httpkit.client.MultipartEntity (type obj)) (bean obj)
                  :else obj))
              (:form-params request)))

(defhandler upload-data
  {:summary    "Upload data via multipart"
   :parameters {:form-params Upload}
   :responses  {200 {:body {:content-type s/Str
                            :content-map {s/Keyword s/Any}}}}}
  [request]
  {:status 200
   :body {:content-type (get-content-type-header request)
          :content-map (get-prepared-content-map request)}})

(def something
  {:status 200
   :body   {:message "Here's some text content"}})

(defhandler get-something
  {:summary   "Get something to test response coercion"
   :responses {200 {:body {:message s/Str}}}}
  [request]
  something)

;; Pedestal doesn't cater for form-urlencoded data
(defhandler get-something-as-form-data
  {:summary   "Same as `get-something`, but returns form-urlencoded data"
   :responses {200 {:body s/Str}}}
  [request]
  (-> something
      (assoc :headers {"Content-Type" "application/x-www-form-urlencoded"})
      (update :body codec/form-encode)))

(defhandler get-something-magical
  {:summary   "Same as `get-something`, but returns 'application/magical+json'"
   :responses {200 {:body s/Str}}}
  [request]
  (-> something
      (assoc :headers {"Content-Type" "application/magical+json"})
      (update :body (comp str/reverse json/generate-string))))

(s/with-fn-validation
  (api/defroutes routes
    {}
    [[["/" ^:interceptors [api/error-responses
                           (api/negotiate-response)
                           (api/body-params)
                           api/common-body
                           (api/coerce-request)
                           (api/validate-response)]
       ["/pets"
        ["/" {:post create-pet}]
        ["/:id" {:get get-pet}]]

       ;; endpoint for multipart request tests
       ["/upload" {:post upload-data}]

       ;; endpoints for response coercions tests
       ["/edn" {:get [:get-edn get-something]}]
       ["/json" {:get [:get-json get-something]}]
       ["/transit+json" {:get [:get-transit+json get-something]}]
       ["/transit+msgpack" {:get [:get-transit+msgpack get-something]}]
       ["/form-data" {:get [:get-form-data get-something-as-form-data]}]
       ["/something" {:get [:get-something get-something]}]
       ["/anything" {:get [:get-anything get-something]}]
       ["/magical" {:get [:get-magical get-something-magical]}]

       ["/swagger.json" {:get api/swagger-json}]]]]))

(def service
  {:env                        :dev
   ::bootstrap/routes          #(deref #'routes)
   ;; linear-search, and declaring the swagger-ui handler last in the routes,
   ;; is important to avoid the splat param for the UI matching API routes
   ::bootstrap/router          :linear-search
   ::bootstrap/resource-path   "/public"
   ::bootstrap/type            :jetty
   ::bootstrap/port            8888
   ::bootstrap/join?           false
   ::bootstrap/allowed-origins {:creds true
                                :allowed-origins (constantly true)}})

(def swagger-url (format "http://localhost:%s/swagger.json" (::bootstrap/port service)))
(def openapi-url (format "http://localhost:%s/openapi.json" (::bootstrap/port service)))
(def openapi-yaml-url (format "http://localhost:%s/openapi.yaml" (::bootstrap/port service)))
(def openapi-test-url (format "http://localhost:%s/openapi-test.json" (::bootstrap/port service)))
(def openapi-test-yaml-url (format "http://localhost:%s/openapi-test.yaml" (::bootstrap/port service)))
(def openapi-multipart-url (format "http://localhost:%s/openapi-multipart.json" (::bootstrap/port service)))
(def test-multipart-file-url (format "http://localhost:%s/test-multipart.txt" (::bootstrap/port service)))
(def openapi-coercions-url (format "http://localhost:%s/openapi-coercions.json" (::bootstrap/port service)))

(defn add-interceptors
  [service-map]
  (update service-map
          ::bootstrap/interceptors
          #(into [(ring-mw/multipart-params)] %)))

;; For use at the REPL

(defonce server-instance (atom nil))

(defn stop-server! []
  (when-let [instance @server-instance]
    (bootstrap/stop instance)
    (reset! server-instance nil)))

(defn start-server! []
  (when @server-instance
    (stop-server!))
  (let [instance (-> (bootstrap/default-interceptors service)
                     (add-interceptors)
                     (bootstrap/create-server))]
    (bootstrap/start instance)
    (reset! server-instance instance)))

;; For test fixtures

(def with-server
  (fn [f]
    (start-server!)
    (try
      (f)
      (finally
        (stop-server!)))))
