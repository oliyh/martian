(ns martian.server-stub
  (:require [io.pedestal.http :as bootstrap]
            [pedestal-api
             [core :as api]
             [helpers :refer [before defbefore defhandler handler]]]
            [schema.core :as s]))

(defonce the-pets (atom {}))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(defhandler create-pet
  {:summary     "Create a pet"
   :parameters  {:body-params Pet}
   :responses   {201 {:body {:id s/Int}}}}
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

(def with-server
  (fn [f]
    (let [server-instance (bootstrap/create-server (bootstrap/default-interceptors service))]
      (try
        (bootstrap/start server-instance)
        (f)
        (finally (bootstrap/stop server-instance))))))
