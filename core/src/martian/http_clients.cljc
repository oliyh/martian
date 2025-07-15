(ns martian.http-clients
  (:require [martian.interceptors :as i]))

(def go-async i/remove-stack)

(defn prepare-opts
  [build-custom-opts-fn supported-opts default-opts opts]
  (if (and (seq opts)
           (some (set (keys opts)) supported-opts))
    (merge (build-custom-opts-fn opts)
           (apply dissoc opts supported-opts))
    default-opts))

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
