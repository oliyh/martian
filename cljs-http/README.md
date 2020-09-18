## martian-cljs-http

A cljs-http implementation for Martian

Note that `bootstrap-openapi` and `bootstrap-swagger` 
return a core.async channel which delivers a Martian instance, 
rather than the instance directly.

```clj
(require '[martian.core :as martian])
(require '[martian.cljs-http :as martian-http])
(require '[cljs.core.async :refer [<!]])
(require-macros '[cljs.core.async.macros :refer [go]])

(go 
  (let [m (<! (martian-http/bootstrap-swagger "")]
    (println (martian/explore m))))
```
