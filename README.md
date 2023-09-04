# Martian
Calling HTTP endpoints can be complicated. You have to construct the right URL with the right route parameters, remember
what the query parameters are, what method to use, how to encode the body and many other things that leak into your codebase.

**Martian** takes a description of these details (either from your [OpenAPI/Swagger](http://swagger.io/) server,
or just as lovely Clojure data) and provides a client interface to the API that abstracts you away from HTTP and lets you
simply call operations with parameters, keeping your codebase clean.

You can bootstrap it in one line and start calling the server:
```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

(let [m (martian-http/bootstrap-openapi "https://pedestal-api.herokuapp.com/swagger.json")]
  (martian/response-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})
  ;; => {:status 201 :body {:id 123}}

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200 :body {:name "Doggy McDogFace" :type "Dog" :age 3}}
```

Implementations using [clj-http](https://github.com/dakrone/clj-http), [clj-http-lite](https://github.com/clj-commons/clj-http-lite), [httpkit](https://github.com/http-kit/http-kit),
[hato](https://github.com/gnarroway/hato), [cljs-http](https://github.com/r0man/cljs-http) and [cljs-http-promise](https://github.com/oliyh/cljs-http-promise) are supplied as modules,
but any other HTTP library can be used due to the extensibility of Martian's interceptor chain.
It also allows custom behaviour to be injected in a uniform and powerful way.

The `martian-test` library allows you to assert that your code constructs valid requests to remote servers without ever
actually calling them, using the OpenApi spec to validate the parameters. It can also generate responses in the same way,
ensuring that your response handling code is also correct. Examples are below.

`martian-re-frame` integrates martian event handlers into `re-frame`, simplifying connecting your UI to data sources.

## Latest versions & API docs

The core library (required):

[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian.svg)](https://clojars.org/com.github.oliyh/martian) [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian)](https://cljdoc.org/d/com.github.oliyh/martian/CURRENT)

Support for various HTTP client libraries:
|HTTP client|JVM|Javascript|Babashka|Docs|
| --------- | --- | ------ | ------ | --- |
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-hato.svg)](https://clojars.org/com.github.oliyh/martian-hato)|✔|||[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-hato)](https://cljdoc.org/d/com.github.oliyh/martian-hato/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-clj-http.svg)](https://clojars.org/com.github.oliyh/martian-clj-http)|✔|||[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-clj-http)](https://cljdoc.org/d/com.github.oliyh/martian-clj-http/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-clj-http-lite.svg)](https://clojars.org/com.github.oliyh/martian-clj-http-lite)|✔||✔|[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-clj-http-lite)](https://cljdoc.org/d/com.github.oliyh/martian-clj-http-lite/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-httpkit.svg)](https://clojars.org/com.github.oliyh/martian-httpkit)|✔||✔|[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-httpkit)](https://cljdoc.org/d/com.github.oliyh/martian-httpkit/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-cljs-http.svg)](https://clojars.org/com.github.oliyh/martian-cljs-http)||✔||[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-cljs-http)](https://cljdoc.org/d/com.github.oliyh/martian-cljs-http/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-cljs-http-promise.svg)](https://clojars.org/com.github.oliyh/martian-cljs-http-promise)||✔||[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-cljs-http-promise)](https://cljdoc.org/d/com.github.oliyh/martian-cljs-http-promise/CURRENT)|
|[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-babashka-http-client.svg)](https://clojars.org/com.github.oliyh/martian-babashka-http-client)|||✔|[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-babashka-http-client)](https://cljdoc.org/d/com.github.oliyh/martian-babashka-http-client/CURRENT)|

Testing and other interop libraries:

[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-test.svg)](https://clojars.org/com.github.oliyh/martian-test)

[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-vcr.svg)](https://clojars.org/com.github.oliyh/martian-vcr) [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-vcr)](https://cljdoc.org/d/com.github.oliyh/martian-vcr/CURRENT)

[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-re-frame.svg)](https://clojars.org/com.github.oliyh/martian-re-frame) [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-re-frame)](https://cljdoc.org/d/com.github.oliyh/martian-re-frame/CURRENT)


## Features
- Bootstrap an instance from just a OpenAPI/Swagger url, a local definition file or provide your own API mapping
- Modular with support for many HTTP client libraries (see table above)
- Build urls and request maps from code or generate and perform the request, returning the response
- Validate requests and responses to ensure they are correct before the data leaves/enters your system
- Explore an API from your REPL
- Extensible via interceptor pattern - inject your own interceptors anywhere in the chain
- Negotiates the most efficient content-type and handles serialisation and deserialisation including `transit`, `edn` and `json`
- Easy to add support for any other content-type
- Support for integration testing without requiring external HTTP stubs
- Routes are named as idiomatic kebab-case keywords of the `operationId` of the endpoint in the OpenAPI/Swagger definition
- Parameters are aliased to kebab-case keywords so that your code remains idiomatic, neat and clean
- Parameter defaults can be optionally applied
- Simple, data driven behaviour with low coupling using libraries and patterns you already know
- Pure client code, no server code or modifications required
- Write generative, realistic tests using [martian-test](https://github.com/oliyh/martian/tree/master/test) to generate response data
- Record and play back HTTP calls using [martian-vcr](https://github.com/oliyh/martian/tree/master/vcr)

For more details and rationale you can [watch the talk given to London Clojurians](https://www.youtube.com/watch?v=smzc8XlvlSQ) or there is also an older [talk given at ClojureX Bytes](https://skillsmatter.com/skillscasts/8843-clojure-bytes#video).

## Clojure / ClojureScript

Given an [OpenAPI/Swagger API definition](https://pedestal-api.herokuapp.com/swagger.json)
like that provided by [pedestal-api](https://github.com/oliyh/pedestal-api):

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

;; bootstrap the martian instance by simply providing the url serving the openapi/swagger description
(let [m (martian-http/bootstrap-openapi "https://pedestal-api.herokuapp.com/swagger.json")]

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

Note that when calling `bootstrap-openapi` you can also provide a url to a local resource, e.g. `(martian-http/bootstrap-openapi "public/openapi.json")`.
For ClojureScript the file can only be read at compile time, so a slightly different form is required using the `martian.file/load-local-resource` macro:
```clj
(martian/bootstrap-openapi "https://sandbox.example.com" (load-local-resource "openapi-test.json") martian-http/default-opts)
```

## No Swagger, no problem

Although bootstrapping against a remote OpenAPI or Swagger API using `bootstrap-openapi` is simplest
and allows you to use the golden source to define the API, you may likely find yourself
needing to integrate with an API beyond your control which does not use OpenAPI or Swagger.

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

(let [m (-> (martian-http/bootstrap-openapi "https://pedestal-api.herokuapp.com/swagger.json")
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

More documentation is available at [martian-test](https://github.com/oliyh/martian/tree/master/test).

## Recording and playback with martian-vcr
martian-vcr allows you to record responses from real HTTP requests and play them back later, allowing you to build realistic test
data quickly and easily.

```clj
(require '[martian.vcr :as vcr])

(def m (http/bootstrap "https://foo.com/api"
                       {:interceptors (inject http/default-interceptors
                                              (vcr/record opts)
                                              :after http/perform-request)}))

(m/response-for m :load-pet {:id 123})
;; the response is recorded and now stored at test-resources/vcr/load-pet/-655390368/0.edn
```

More documentation is available at [martian-vcr](https://github.com/oliyh/martian/tree/master/vcr).

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
(request-for m :create-pet {:pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}})           ;; the :pet alias
(request-for m :create-pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"})                  ;; destructured
(request-for m :create-pet {::martian/body {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}}) ;; explicit body value

```

## Custom behaviour

You may wish to provide additional behaviour to requests. This can be done by providing Martian with interceptors
which behave in the same way as pedestal interceptors.

### Global behaviour
You can add interceptors to the stack that get executed on every request when bootstrapping martian.
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

(let [m (martian-http/bootstrap-openapi
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

Alternatively you can use the helpers like `update-handler` to update a martian created from `bootstrap-openapi`:

```clojure
(-> (martian/bootstrap-openapi "https://api.org" openapi-definition)
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
(require '[clojure.string :as str])

(def magic-encoder str/upper-case)
(def magic-decoder str/lower-caser)

(let [encoders (assoc (encoders/default-encoders)
                      "application/magical" {:encode magic-encoder
                                             :decode magic-decoder
                                             :as :magic})]
  (http/bootstrap-openapi
   "https://example-api.com"
   {:interceptors (concat m/default-interceptors
                          [(i/encode-body encoders)
                           (i/coerce-response encoders)
                           http/perform-request])}))

```

## Response validation

Martian provides a response validation interceptor which validates the response against the response schemas.
It is not included in the default interceptor stack, but you can include it yourself:

```clojure
(http/bootstrap-openapi
 "https://example-api.com"
 {:interceptors (cons (i/validate-response {:strict? true})
                      http/default-interceptors)})
```

The `strict?` argument defines whether any response with an undefined schema is allowed, e.g. if a response
schema is defined for a 200 status code only, but the server returns a 500, strict mode will throw an error but
non-strict mode will allow it. Strict mode defaults to false.

## Defaults

Martian can read `default` directives from Swagger, or you can supply them if bootstrapping from data. They can be seen using `explore` and merged with your params if you set the optional `use-defaults?` option.

```clojure
(require '[schema-tools.core :as st])
(require '[martian.interceptors :refer [merge-defaults]])

(let [m (martian/bootstrap "https://api.org"
                           [{:route-name :create-pet
                             :path-parts ["/pets/"]
                             :method :post
                             :body-schema {:pet {:id   s/Int
                                                 :name (st/default s/Str "Bryson")}}}]
                           {:use-defaults? true})]

  (martian/explore m :create-pet)
  ;; {:summary nil, :parameters {:pet {:id Int, :name (default Str "Bryson")}}, :returns {}}

  (martian/request-for m :create-pet {:pet {:id 123}})
  ;; {:method :post, :url "https://api.org/pets/", :body {:id 123, :name "Bryson"}}
  )
```

## Development mode

When martian is bootstrapped it closes over the route definitions and any options you provide, returning an immutable instance.
This can hamper REPL development when you wish to rapidly iterate on your martian definition, so all martian API calls also accept a function or a var that returns the instance instead:

```clojure
(martian/url-for (fn [] (martian/bootstrap ... )) :load-pet {:id 123}) ;; => "https://api.com/pets/123"
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
- You need `:operationId` in the OpenAPI/Swagger spec to name routes when using `bootstrap-openapi`
  - [pedestal-api](https://github.com/oliyh/pedestal-api) automatically generates these from the route name

## Development
[![Circle CI](https://circleci.com/gh/oliyh/martian.svg?style=svg)](https://circleci.com/gh/oliyh/martian)

Use `cider-jack-in-clj` or `cider-jack-in-clj&cljs` to start Clojure (and Clojurescript where appropriate) REPLs for development.
You may need to `lein install` first if you're working in a module that depends on another.

## Issues and features
Please feel free to raise issues on Github or send pull requests.

## Acknowledgements
Martian uses [tripod](https://github.com/frankiesardo/tripod) for routing, inspired by [pedestal](https://github.com/pedestal/pedestal).
