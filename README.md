# Martian
Calling HTTP endpoints can be complicated. You have to construct the right URL with the right route parameters, remember
what the query parameters are, what method to use, how to encode the body and many other things that leak into your codebase.

**Martian** takes a description of these details (either from your [Swagger](http://swagger.io/) server,
or just as lovely Clojure data) and provides a client interface to the API that abstracts you away from HTTP and lets you
simply call operations with parameters, keeping your codebase clean.

You can bootstrap it in one line and start calling the server:
```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

(let [m (martian-http/bootstrap-swagger "https://pedestal-api.herokuapp.com/swagger.json")]
  (martian/response-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})
  ;; => {:status 201 :body {:id 123}}

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200 :body {:name "Doggy McDogFace" :type "Dog" :age 3}}
```

Implementations using `clj-http`, `clj-http-lite`, `httpkit` and `cljs-http` are supplied as modules,
but any other HTTP library can be used due to the extensibility of Martian's interceptor chain.
It also allows custom behaviour to be injected in a uniform and powerful way.

The `martian-test` library allows you to assert that your code constructs valid requests to remote servers without ever
actually calling them, using the Swagger spec to validate the parameters. It can also generate responses in the same way,
ensuring that your response handling code is also correct. Examples are below.

`martian-re-frame` integrates martian event handlers into `re-frame`, simplifying connecting your UI to data sources.

## Latest versions & API docs
[![Clojars Project](https://img.shields.io/clojars/v/martian.svg)](https://clojars.org/martian) [![cljdoc badge](https://cljdoc.org/badge/martian/martian)](https://cljdoc.org/d/martian/martian/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/martian-clj-http.svg)](https://clojars.org/martian-clj-http) [![cljdoc badge](https://cljdoc.org/badge/martian-clj-http/martian-clj-http)](https://cljdoc.org/d/martian-clj-http/martian-clj-http/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/martian-clj-http-lite.svg)](https://clojars.org/martian-clj-http-lite) [![cljdoc badge](https://cljdoc.org/badge/martian-clj-http-lite/martian-clj-http-lite)](https://cljdoc.org/d/martian-clj-http-lite/martian-clj-http-lite/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/martian-httpkit.svg)](https://clojars.org/martian-httpkit) [![cljdoc badge](https://cljdoc.org/badge/martian-httpkit/martian-httpkit)](https://cljdoc.org/d/martian-httpkit/martian-httpkit/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/martian-cljs-http.svg)](https://clojars.org/martian-cljs-http) [![cljdoc badge](https://cljdoc.org/badge/martian-cljs-http/martian-cljs-http)](https://cljdoc.org/d/martian-cljs-http/martian-cljs-http/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/martian-test.svg)](https://clojars.org/martian-test)

[![Clojars Project](https://img.shields.io/clojars/v/martian-re-frame.svg)](https://clojars.org/martian-re-frame) [![cljdoc badge](https://cljdoc.org/badge/martian-re-frame/martian-re-frame)](https://cljdoc.org/d/martian-re-frame/martian-re-frame/CURRENT)

## Features
- Bootstrap an instance from just a Swagger url or provide your own API mapping
- Modular with support for `clj-http` and `httpkit` (Clojure) and `cljs-http` (ClojureScript)
- Build urls and request maps from code or generate and perform the request, returning the response
- Explore an API from your REPL
- Extensible via interceptor pattern - inject your own interceptors anywhere in the chain
- Negotiates the most efficient content-type and handles serialisation and deserialisation including `transit`, `edn` and `json`
- Easy to add support for any other content-type
- Support for integration testing without requiring external HTTP stubs
- Routes are named as idiomatic kebab-case keywords of the `operationId` of the endpoint in the Swagger definition
- Parameters are aliased to kebab-case keywords so that your code remains idiomatic, neat and clean
- Simple, data driven behaviour with low coupling using libraries and patterns you already know
- Pure client code, no server code or modifications required

For more details and rationale you can [watch the talk given at ClojureX Bytes](https://skillsmatter.com/skillscasts/8843-clojure-bytes#video).

## Clojure / ClojureScript

Given a [Swagger API definition](https://pedestal-api.herokuapp.com/swagger.json)
like that provided by [pedestal-api](https://github.com/oliyh/pedestal-api):

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

;; bootstrap the martian instance by simply providing the url serving the swagger description
(let [m (martian-http/bootstrap-swagger "https://pedestal-api.herokuapp.com/swagger.json")]

  ;; explore the endpoints
  (martian/explore m)
  ;; => [[:get-pet "Loads a pet by id"]
  ;;     [:create-pet "Creates a pet"]]

  ;; explore the :get-pet endpoint
  (martian/explore m :get-pet)
  ;; => {:summary "Loads a pet by id"
  ;;     :parameters {:id s/Int}}

  ;; build the url for a request
  (martian/url-for m :get-pet {:id 123})
  ;; => https://pedestal-api.herokuapp.com/pets/123

  ;; build the request map for a request
  (martian/request-for m :get-pet {:id 123})
  ;; => {:method :get
  ;;     :url "https://pedestal-api.herokuapp.com/pets/123"
  ;;     :headers {"Accept" "application/transit+msgpack"
  ;;     :as :byte-array}

  ;; perform the request to create a pet and read back the pet-id from the response
  (let [pet-id (-> (martian/response-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})
                   (get-in [:body :id]))]

    ;; load the pet using the id
    (martian/response-for m :get-pet {:id pet-id}))

    ;; => {:status 200
    ;;     :body {:name "Doggy McDogFace"
    ;;            :type "Dog"
    ;;            :age 3}}

  ;; :martian.core/body can optionally be used in lieu of explicitly naming the body schema
  (let [pet-id (-> (martian/response-for m :create-pet {::martian/body {:name "Doggy McDogFace" :type "Dog" :age 3}})
                   (get-in [:body :id]))])

  ;; the name of the body object can also be used to nest the body parameters
  (let [pet-id (-> (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace" :type "Dog" :age 3}})
                   (get-in [:body :id]))]))
```

## No Swagger, no problem

Although bootstrapping against a remote Swagger API using `bootstrap-swagger` is simplest
and allows you to use the golden source to define the API, you may likely find yourself
needing to integrate with an API beyond your control which does not use Swagger.

Martian offers a separate `bootstrap` function which you can provide with handlers defined as data.
Here's an example:

```clojure
(martian/bootstrap "https://api.org"
                   [{:route-name :load-pet
                     :path-parts ["/pets/" :id]
                     :method :get
                     :path-schema {:id s/Int}}

                    {:route-name :create-pet
                     :produces ["application/xml"]
                     :consumes ["application/xml"]
                     :path-parts ["/pets/"]
                     :method :post
                     :body-schema {:pet {:id   s/Int
                                         :name s/Str}}}])

```

## Testing with martian-test
Testing code that calls external systems can be tricky - you either build often elaborate stubs which start
to become as complex as the system you are calling, or else you ignore it all together with `(constantly true)`.

Martian will assert that you provide the right parameters to the call, and `martian-test` will return a response
generated from the response schema of the remote application. This gives you more confidence that your integration is
correct without maintenance of a stub.

The following example shows how exceptions will be thrown by bad code and how responses can be generated:
```clojure
(require '[martian.core :as martian]
         '[martian.httpkit :as martian-http]
         '[martian.test :as martian-test])

(let [m (-> (martian-http/bootstrap-swagger "https://pedestal-api.herokuapp.com/swagger.json")
            (martian-test/respond-with-generated {:get-pet :random}))]

  (martian/response-for m :get-pet {})
  ;; => ExceptionInfo Value cannot be coerced to match schema: {:id missing-required-key}

  (martian/response-for m :get-pet {:id "bad-id"})
  ;; => ExceptionInfo Value cannot be coerced to match schema: {:id (not (integer? bad-id))}

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200, :body {:id -3, :name "EcLR"}}

```
`martian-test` has interceptors that always give successful responses, always errors, or a random choice.
By making your application code accept a Martian instance you can inject a test instance within your tests, making
previously untestable code testable again.

## Idiomatic parameters

If an API has a parameter called `FooBar` it's difficult to stop that leaking into your own code - the Clojure idiom is to
use kebab-cased keywords such as `:foo-bar`. Martian maps parameters to their kebab-cased equivalents so that your code looks neater
but preserves the mapping so that the API is passed the correct parameter names:

```clojure
(let [m (martian/bootstrap "https://api.org"
                           [{:route-name  :create-pet
                             :path-parts  ["/pets/"]
                             :method      :post
                             :body-schema {:pet {:PetId     s/Int
                                                 :FirstName s/Str
                                                 :LastName  s/Str}}}])]

  (martian/request-for m :create-pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}))

;; => {:method :post
;;     :url    "https://api.org/pets/"
;;     :body   {:PetId     1
;;              :FirstName "Doggy"
;;              :LastName  "McDogFace"}}
```

Body parameters may be supplied in three ways: with an alias, destructured or as an explicit value.

```clojure
;; the following three forms are equivalent
(request-for :create-pet {:pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}})           ;; the :pet alias
(request-for :create-pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"})                  ;; destructured
(request-for :create-pet {::martian/body {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}}) ;; explicit body value

```

## Custom behaviour

You may wish to provide additional behaviour to requests. This can be done by providing Martian with interceptors
which behave in the same way as pedestal interceptors.

### Global behaviour
You can add interceptors to the stack that gets executed on every request when bootstrapping martian.
For example, if you wish to add an authentication header and a timer to all requests:

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

(def add-authentication-header
  {:name ::add-authentication-header
   :enter (fn [ctx]
            (assoc-in ctx [:request :headers "Authorization"] "Token: 12456abc"))})

(def request-timer
  {:name ::request-timer
   :enter (fn [ctx]
            (assoc ctx ::start-time (System/currentTimeMillis)))
   :leave (fn [ctx]
            (->> ctx ::start-time
                 (- (System/currentTimeMillis))
                 (format "Request to %s took %sms" (get-in ctx [:handler :route-name]))
                 (println))
            ctx)})

(let [m (martian-http/bootstrap-swagger
               "https://pedestal-api.herokuapp.com/swagger.json"
               {:interceptors (concat martian/default-interceptors
                                      [add-authentication-header
                                       martian-http/encode-body
                                       (martian-http/coerce-response)
                                       request-timer
                                       martian-http/perform-request])})]

        (martian/response-for m :all-pets {:id 123}))
        ;; Request to :all-pets took 38ms
        ;; => {:status 200 :body {:pets []}}
```

### Per route behaviour

Sometimes individual routes require custom behaviour. This can be achieved by writing a
global interceptor which inspects the route-name and decides what to do, but a more specific
option exists using `bootstrap` and providing `:interceptors` as follows:

```clojure
(martian/bootstrap "https://api.org"
                   [{:route-name :load-pet
                     :path-parts ["/pets/" :id]
                     :method :get
                     :path-schema {:id s/Int}
                     :interceptors [{:name ::override-load-pet-method
                                     :enter #(assoc-in % [:request :method] :xget)}]}])
```

Alternatively you can use the helpers like `update-handler` to update a martian created from `bootstrap-swagger`:

```clojure
(-> (martian/bootstrap-swagger "https://api.org" swagger-definition)
    (martian/update-handler :load-pet assoc :interceptors [{:name ::override-load-pet-method
                                                            :enter #(assoc-in % [:request :method] :xget)}]))
```

Interceptors provided at a per-route level are inserted into the interceptor chain at execution time by the interceptor called
`:martian.interceptors/enqueue-route-specific-interceptors`. This results in the following chain:

- `set-method`
- `set-url`
- `set-query-params`
- `set-body-params`
- `set-form-params`
- `set-header-params`
- `enqueue-route-specific-interceptors` - injects the following at runtime:
  - `route-interceptor-1` e.g. `::override-load-pet-method`
  - `route-interceptor-2`
  - etc
- `encode-body`
- `default-coerce-response`
- `perform-request`

This means your route interceptors have available to them the unserialised request on enter and the deserialised response on leave.
You may move or provide your own version of `enqueue-route-specific-interceptors` to change this behaviour.

## Custom content-types

Martian allows you to add support for content-types in addition to those supported out of the box - `transit`, `edn` and `json`.

```clojure
(require '[martian.core :as m])
(require '[martian.httpkit :as http])
(require '[martian.encoders :as encoders])
(require '[martian.interceptors :as i])

(let [encoders (assoc (encoders/default-encoders)
                      "application/magical" {:encode magic-encoder
                                             :decode magic-decoder
                                             :as :magic})]
  (http/bootstrap-swagger
   "https://example-api.com"
   {:interceptors (concat m/default-interceptors
                          [(i/encode-body encoders)
                           (i/coerce-response encoders)
                           http/perform-request])}))

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
- You need `:operationId` in the Swagger spec to name routes when using `bootstrap-swagger`
  - [pedestal-api](https://github.com/oliyh/pedestal-api) automatically generates these from the route name

## Development
[![Circle CI](https://circleci.com/gh/oliyh/martian.svg?style=svg)](https://circleci.com/gh/oliyh/martian)

Use `cider-jack-in-clj` or `cider-jack-in-clj&cljs` to start Clojure (and Clojurescript where appropriate) REPLs for development.

## Issues and features
Please feel free to raise issues on Github or send pull requests.

## Acknowledgements
Martian uses [tripod](https://github.com/frankiesardo/tripod) for routing, inspired by [pedestal](https://github.com/pedestal/pedestal).
See also [fintrospect](http://fintrospect.io/) and [kekkonen](https://github.com/metosin/kekkonen) for ideas integrating server and client beyond Swagger.
