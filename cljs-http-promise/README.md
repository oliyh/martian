## martian-cljs-http

A [cljs-http-promise](https://github.com/oliyh/cljs-http-promise) implementation for Martian

Note that `bootstrap-openapi` and `bootstrap-swagger`
return a promesa promise which delivers a Martian instance,
rather than the instance directly.

```clj
(require '[martian.core :as martian])
(require '[martian.cljs-http-promise :as martian-http])
(require '[promesa.core :as prom])

(prom/then (martian-http/bootstrap-swagger "")
  (fn [m]
    (println (martian/explore m))))
```
