(ns martian.cljs-http-promise
  (:require [cljs-http.client :as http]
            [martian.core :as martian]
            [martian.interceptors :as i]
            [martian.openapi :refer [openapi-schema?] :as openapi]
            [tripod.context :as tc]
            [clojure.string :as str]
            [promesa.core :as prom]))

(def ^:private go-async
  i/remove-stack)

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (prom/then (http/request request)
                                  (fn [response]
                                    (:response (tc/execute (assoc ctx :response response))))))))})

(def default-interceptors
  (concat martian/default-interceptors [i/default-encode-body i/default-coerce-response perform-request]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [trim-base-url?] :as opts}]]
  (prom/then (http/get url {:as :json})
             (fn [response]
               (let [definition (:body response)
                     {:keys [scheme server-name server-port]} (http/parse-url url)
                     raw-base-url (str (when-not (re-find #"^/" url)
                                         (str (name scheme) "://" server-name (when server-port (str ":" server-port))))
                                       (if (openapi-schema? definition)
                                         (openapi/base-url definition)
                                         (get definition :basePath "")))
                     base-url (if trim-base-url?
                                (str/replace raw-base-url #"/$" "")
                                raw-base-url)]
                 (martian/bootstrap-openapi base-url definition (merge default-opts opts))))))

(def bootstrap-swagger bootstrap-openapi)
