(ns martian.http-clients
  (:require [martian.interceptors :as i]))

(def go-async i/remove-stack)

(defn prepare-opts
  [build-custom-opts-fn supported-custom-opts default-opts opts]
  (merge default-opts
         (let [custom-opts (select-keys opts supported-custom-opts)]
           (when (seq custom-opts)
             (build-custom-opts-fn custom-opts)))
         (apply dissoc opts supported-custom-opts)))

(defn update-basic-interceptors
  [basic-interceptors
   {:keys [request-encoders response-encoders response-coerce-opts]}]
  (cond-> basic-interceptors

    (some? request-encoders)
    (i/inject (i/encode-request request-encoders)
              :replace ::i/encode-request)

    (some? response-encoders)
    (i/inject (i/coerce-response response-encoders response-coerce-opts)
              :replace ::i/coerce-response)))
