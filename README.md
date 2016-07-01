# martian
Build and execute requests on your Clojure/Clojurescript/Java client to a Swagger API without the minutiae
of HTTP verbs, route parameters, query parameters and the like.

Martian allows you to abstract yourself from the fine details of a Swagger implementation and simply call
operations with parameters.

Various implementations of HTTP libraries are supplied, but any other can be used due to the extensibility of martian's
interceptor chain. It also allows custom behaviour to be injected in a uniform and powerful way.

The martian-test library allows you to assert that your code constructs valid requests to remote servers without ever
actually calling them, using the Swagger spec to validate the parameters. It can also generate responses in the same way,
ensuring that your response handling code is also correct.

## Latest versions
[![Clojars Project](https://img.shields.io/clojars/v/martian.svg)](https://clojars.org/martian)
[![Clojars Project](https://img.shields.io/clojars/v/martian-clj-http.svg)](https://clojars.org/martian-clj-http)
[![Clojars Project](https://img.shields.io/clojars/v/martian-httpkit.svg)](https://clojars.org/martian-httpkit)
[![Clojars Project](https://img.shields.io/clojars/v/martian-cljs-http.svg)](https://clojars.org/martian-cljs-http)
[![Clojars Project](https://img.shields.io/clojars/v/martian-test.svg)](https://clojars.org/martian-test)

## Features
- Bootstraps itself from just a Swagger url
- Negotiates the most efficient content-type and handles serialisation and deserialisation
- Extensible via interceptor pattern
- Support for integration testing without requiring external HTTP stubs

## Example
Given a [Swagger API definition](https://pedestal-api.herokuapp.com/swagger.json)
like that provided by [pedestal-api](https://github.com/oliyh/pedestal-api):

### Clojure / ClojureScript
```clojure
(require '[martian.protocols :refer [url-for request-for]]
         '[martian.clj-http :as martian-http])

(let [m (martian-http/bootstrap-swagger "https://pedestal-api.herokuapp.com/swagger.json")]

  (url-for m :get-pet {:id 123}))
  ;; => https://pedestal-api.herokuapp.com/pets/123

  (let [pet-id (:id (:body (request-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})))]

    (request-for m :get-pet {:id pet-id}))
    ;; => {:status 200
           :body {:name "Doggy McDogFace"
                  :type "Dog"
                  :age 3}}
```

## Java

```java
import martian.Martian;
import java.util.Map;
import java.util.HashMap;

Map<String, Object> swaggerSpec = { ... };
Martian martian = new Martian("https://pedestal-api.herokuapp.com", swaggerSpec);

martian.urlFor("get-pet", new HashMap<String, Object> {{ put("id", 123); }});

// => https://pedestal-api.herokuapp.com/pets/123
```

## Caveats
- You need `:operationId` in the Swagger spec to name routes
  - [pedestal-api](https://github.com/oliyh/pedestal-api) automatically generates these from the route name

## Development
[![Circle CI](https://circleci.com/gh/oliyh/martian.svg?style=svg)](https://circleci.com/gh/oliyh/martian)

Step in to the Clojurescript REPL as follows:
```clojure
(cemerick.piggieback/cljs-repl (cljs.repl.rhino/repl-env))
```

## Acknowledgements
martian uses [tripod](https://github.com/frankiesardo/tripod) for routing.
See also [kekkonen](https://github.com/metosin/kekkonen) for ideas integrating server and client beyond Swagger.
