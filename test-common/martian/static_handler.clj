(ns martian.static-handler
  (:require [ring.middleware.resource :refer [wrap-resource]]))

;; Serves classpath resources under the "public/" prefix.
;; Used by figwheel's built-in Jetty server so that relative-URL XHRs
;; from the browser (e.g. GET /openapi-test.json) resolve correctly
;; against figwheel's origin rather than the Pedestal API stub (:8888).
;; The "public" directory is on the classpath via ../test-common in
;; each module's dev :resource-paths.
(def handler
  (wrap-resource
    (fn [_req] {:status 404 :headers {} :body "Not found"})
    "public"))
