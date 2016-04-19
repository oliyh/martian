# martian
Build routes on your Clojure/Clojurescript client to a Swagger API

## Example
Given a [Swagger API definition](https://pedestal-api.herokuapp.com/swagger.json)
like that provided by [pedestal-api](https://github.com/oliyh/pedestal-api):

### Clojure
```clojure
(require '[martian.core :as martian]
         '[clj-http.client :as http])

(let [api-root "https://pedestal-api.herokuapp.com"
      swagger-spec (:body (http/get (str api-root "/swagger.json") {:as :json}))
      url-for (martian/bootstrap api-root swagger-spec)]
  (url-for :get-pet {:id 123}))

;; => https://pedestal-api.herokuapp.com/pets/123
```

## Caveats
- You need `:operationId` in the Swagger spec to name routes
  - pedestal-api automatically generates these from the interceptor name

## Development
Step in to the Clojurescript REPL as follows:
```clojure
(cemerick.piggieback/cljs-repl (cljs.repl.rhino/repl-env))
```

## Acknowledgements
martian uses [tripod](https://github.com/frankiesardo/tripod) for routing.
