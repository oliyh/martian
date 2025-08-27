# Martian

The HTTP abstraction library for Clojure/Script and Babashka, supporting OpenAPI/Swagger and many HTTP client libraries.

---

Calling HTTP endpoints can be complicated. You have to construct the right URL with the right route parameters, remember
what the query parameters are, what method to use, how to encode a request body, coerce a response and many other things
that leak into your codebase.

**Martian** takes a description of these details — either from your [OpenAPI/Swagger](http://swagger.io/) definition,
or just as [lovely Clojure data](#no-swagger-no-problem) — and provides a client interface to the API that abstracts
you away from HTTP and lets you simply call operations with parameters, keeping your codebase clean.

You can bootstrap it in one line and start calling the server:

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

(def m (martian-http/bootstrap-openapi "https://pedestal-api.oliy.co.uk/swagger.json"))

(martian/response-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})
;; => {:status 201 :body {:id 123}}

(martian/response-for m :get-pet {:id 123})
;; => {:status 200 :body {:name "Doggy McDogFace" :type "Dog" :age 3}}
```

Implementations for many popular HTTP client libraries are supplied as modules (see [below](#supported-http-clients)),
but any other HTTP library can be used due to the extensibility of Martian's interceptor chain. It also allows custom
behaviour to be injected in a uniform and powerful way.

The `martian-test` module allows you to assert that your code constructs valid requests to remote servers without ever
actually calling them, using the OpenAPI/Swagger spec to validate the parameters. It can also generate responses in the
same way, ensuring that your response handling code is also correct. Examples are [below](#testing-with-martian-test).

---

## Table of Contents

1. [Latest versions & API docs](#latest-versions--api-docs)
   - [Core module](#core-module)
   - [Supported HTTP clients](#supported-http-clients)
   - [Testing and interop libraries](#testing-and-interop-libraries)
2. [Features](#features)
3. [Basic usage](#basic-usage)
4. [Bootstrapping from local file](#bootstrapping-from-local-file)
5. [No Swagger, no problem](#no-swagger-no-problem)
6. [Handlers validation](#handlers-validation)
7. [Idiomatic parameters](#idiomatic-parameters)
8. [Parameter defaults](#parameter-defaults)
9. [Route name sources](#route-name-sources)
10. [Built-in media types](#built-in-media-types)
11. [Response validation](#response-validation)
12. [Testing with `martian-test`](#testing-with-martian-test)
    - [Generative testing](#generative-testing)
    - [Non-generative testing](#non-generative-testing)
13. [Recording and playback with `martian-vcr`](#recording-and-playback-with-martian-vcr)
14. [Custom behaviour](#custom-behaviour)
    - [Custom interceptors](#custom-interceptors)
      - [Global interceptors](#global-interceptors)
      - [Per route interceptors](#per-route-interceptors)
    - [Custom coercion matcher](#custom-coercion-matcher)
    - [Built-in encoders options](#built-in-encoders-options)
    - [Custom media types](#custom-media-types)
    - [HTTP client-specific options](#http-client-specific-options)
15. [Development mode](#development-mode)
16. [Java](#java)
17. [Caveats](#caveats)
18. [Development](#development)
19. [Issues and features](#issues-and-features)
20. [Acknowledgements](#acknowledgements)

---

## Latest versions & API docs

### Core module

Add the required dependency to the core Martian module:

| Core Module | API Docs |
| ----------- | -------- |
| [![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian.svg)](https://clojars.org/com.github.oliyh/martian) | [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian)](https://cljdoc.org/d/com.github.oliyh/martian/CURRENT) |

### Supported HTTP clients

Add one more dependency to the module for the target HTTP client library:

| HTTP client | Martian Module | JVM | BB | JS | API Docs |
| ----------- | -------------- | --- | -- | -- | -------- |
| [hato](https://github.com/gnarroway/hato) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-hato.svg)](https://clojars.org/com.github.oliyh/martian-hato)| ✔   |    |    |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-hato)](https://cljdoc.org/d/com.github.oliyh/martian-hato/CURRENT/api/martian.hato)|
| [clj-http](https://github.com/dakrone/clj-http) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-clj-http.svg)](https://clojars.org/com.github.oliyh/martian-clj-http)| ✔   |    |    |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-clj-http)](https://cljdoc.org/d/com.github.oliyh/martian-clj-http/CURRENT/api/martian.clj-http)|
| [clj-http-lite](https://github.com/clj-commons/clj-http-lite) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-clj-http-lite.svg)](https://clojars.org/com.github.oliyh/martian-clj-http-lite)| ✔   | ✔  |    |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-clj-http-lite)](https://cljdoc.org/d/com.github.oliyh/martian-clj-http-lite/CURRENT/api/martian.clj-http-lite)|
| [http-kit](https://github.com/http-kit/http-kit) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-httpkit.svg)](https://clojars.org/com.github.oliyh/martian-httpkit)| ✔   | ✔  |    |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-httpkit)](https://cljdoc.org/d/com.github.oliyh/martian-httpkit/CURRENT/api/martian.httpkit)|
| [bb/http-client](https://github.com/babashka/http-client) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-babashka-http-client.svg)](https://clojars.org/com.github.oliyh/martian-babashka-http-client)| ✔   | ✔  |    |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-babashka-http-client)](https://cljdoc.org/d/com.github.oliyh/martian-babashka-http-client/CURRENT/api/martian.babashka.http-client)|
| [cljs-http](https://github.com/r0man/cljs-http) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-cljs-http.svg)](https://clojars.org/com.github.oliyh/martian-cljs-http)|     |    | ✔  |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-cljs-http)](https://cljdoc.org/d/com.github.oliyh/martian-cljs-http/CURRENT/api/martian.cljs-http)|
| [cljs-http-promise](https://github.com/oliyh/cljs-http-promise) |[![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-cljs-http-promise.svg)](https://clojars.org/com.github.oliyh/martian-cljs-http-promise)|     |    | ✔  |[![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-cljs-http-promise)](https://cljdoc.org/d/com.github.oliyh/martian-cljs-http-promise/CURRENT/api/martian.cljs-http-promise)|

### Testing and interop libraries

Optionally add dependencies on modules for testing and interop:

| Martian Module | Docs | API Docs |
| -------------- | ---- | -------- |
| [![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-test.svg)](https://clojars.org/com.github.oliyh/martian-test) | [README](https://github.com/oliyh/martian/tree/master/test) | [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-test)](https://cljdoc.org/d/com.github.oliyh/martian-test/CURRENT/api/martian.test) |
| [![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-vcr.svg)](https://clojars.org/com.github.oliyh/martian-vcr) | [README](https://github.com/oliyh/martian/tree/master/vcr) | [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-vcr)](https://cljdoc.org/d/com.github.oliyh/martian-vcr/CURRENT/api/martian.vcr) |
| [![Clojars Project](https://img.shields.io/clojars/v/com.github.oliyh/martian-re-frame.svg)](https://clojars.org/com.github.oliyh/martian-re-frame) | [README](https://github.com/oliyh/martian/tree/master/re-frame) |  [![cljdoc badge](https://cljdoc.org/badge/com.github.oliyh/martian-re-frame)](https://cljdoc.org/d/com.github.oliyh/martian-re-frame/CURRENT/api/martian.re-frame) |

The `martian-re-frame` integrates Martian event handlers into [re-frame](https://github.com/day8/re-frame), simplifying
connecting your UI to data sources.


## Features

- Bootstrap from a [OpenAPI/Swagger definition](#basic-usage), a [local file/resource](#bootstrapping-from-local-file),
  or [provide your own API mapping as data](#no-swagger-no-problem)
- Modular with [support for many popular HTTP client libraries](#supported-http-clients)
- Build URLs and request maps from code or generate and perform the request, returning the response
- Validate requests and responses to ensure they are correct before the data leaves/enters your system
- Explore an API from your REPL
- Extensible via interceptor pattern — inject your own interceptors anywhere in the chain
- Negotiates [the most efficient media type](#built-in-media-types) — including `transit`, `edn`, `json` and more —
  and handles both request encoding (serialisation) and response coercion (deserialisation)
- Easy to [add support for any other media type](#custom-media-types) or reconfigure encoders for the built-in ones
- Support for integration testing without requiring external HTTP stubs
- Routes are named as idiomatic kebab-case keywords of the endpoint's `operationId` in the OpenAPI/Swagger definition
  (default) or [generated from the URL (path) pattern, HTTP method, and definition](#route-name-sources)
- Parameters are aliased to kebab-case keywords so that your code remains [idiomatic](#idiomatic-parameters) and neat
- [Parameter defaults](#parameter-defaults) can be optionally applied
- Simple, data driven behaviour with low coupling using libraries and patterns you already know
- Pure client code, no server code or modifications required
- Write generative, realistic tests using [martian-test](#testing-with-martian-test) module to generate response data
- Record and play back HTTP calls in VCR style using [martian-vcr](#recording-and-playback-with-martian-vcr) module

For more details and rationale you can watch:
- [the talk given to London Clojurians](https://www.youtube.com/watch?v=smzc8XlvlSQ) or
- an older [talk given at ClojureX Bytes](https://skillsmatter.com/skillscasts/8843-clojure-bytes#video).

## Basic usage

Given an [OpenAPI/Swagger API definition](https://pedestal-api.oliy.co.uk/swagger.json)
like that provided by [pedestal-api](https://github.com/oliyh/pedestal-api):

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

;; Bootstrap the Martian instance
;; - in this case, by simply providing the URL serving the OpenAPI/Swagger spec
(let [m (martian-http/bootstrap-openapi "https://pedestal-api.oliy.co.uk/swagger.json")]

  ;; Explore all available endpoints
  (martian/explore m)
  ;; => [[:get-pet "Loads a pet by id"]
  ;;     [:create-pet "Creates a pet"]]

  ;; Explore a specific endpoint
  (martian/explore m :get-pet)
  ;; => {:summary "Loads a pet by id"
  ;;     :parameters {:id s/Int}}

  ;; Build the URL for a request
  (martian/url-for m :get-pet {:id 123})
  ;; => https://pedestal-api.oliy.co.uk/pets/123

  ;; Build the request map for a request
  (martian/request-for m :get-pet {:id 123})
  ;; => {:method :get
  ;;     :url "https://pedestal-api.oliy.co.uk/pets/123"
  ;;     :headers {"Accept" "application/transit+msgpack"}
  ;;     :as :byte-array}

  ;; Perform the request (to create a pet and read back the `pet-id` from the response)
  (let [pet-id (-> (martian/response-for m :create-pet {:name "Doggy McDogFace" :type "Dog" :age 3})
                   (get-in [:body :id]))]

    ;; Perform the request (to load the pet using its `:id` as a request parameter)
    (martian/response-for m :get-pet {:id pet-id}))
    ;; => {:status 200
    ;;     :body {:name "Doggy McDogFace"
    ;;            :type "Dog"
    ;;            :age 3}}

  ;; Perform the request
  ;; - `:martian.core/body` can optionally be used in lieu of explicitly naming the body schema
  (-> (martian/response-for m :create-pet {::martian/body {:name "Doggy McDogFace" :type "Dog" :age 3}})
      (get-in [:body :id]))
  ;; => 2

  ;; Perform the request
  ;; - the name (the `:pet` alias) of the body object can also be used to nest the body params
  (-> (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace" :type "Dog" :age 3}})
      (get-in [:body :id])))
  ;; => 3
```

## Bootstrapping from local file

Note that when calling the `bootstrap-openapi` functions to bootstrap your Martian instance you can also provide a path
to a local file/resource with the OpenAPI/Swagger spec, e.g. `(martian-http/bootstrap-openapi "public/openapi.json")`.

> For ClojureScript the file can only be read at compile time, so a slightly different form is required
> using the `martian.file/load-local-resource` macro:
> 
> ```clojure
> (ns ;; your namespace
>   (:require [martian.core :as martian]
>             [martian.cljs-http :as martian-http])
>   (:require-macros [martian.file :refer [load-local-resource]]))
> 
> (martian/bootstrap-openapi "https://sandbox.example.com"
>                            (load-local-resource "openapi-test.json")
>                            martian-http/default-opts)
> ```

## No Swagger, no problem

Although bootstrapping against a remote OpenAPI/Swagger spec using `bootstrap-openapi` is simplest and allows you to use
the golden source to define the API, you may likely find yourself needing to integrate with an API beyond your control
which does not use OpenAPI/Swagger spec.

Martian offers a separate `bootstrap` function which you can provide with handlers defined as data. Here's an example:

```clojure
(require '[martian.core :as martian]
         '[schema.core :as s])

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

## Handlers validation

Sometimes, especially when [bootstrapping from data](#no-swagger-no-problem), it is desirable to fail fast if invalid
handlers are encountered. There is a `validate-handlers?` option that enables such early validation of handlers, which
causes any bootstrapping function to throw:

```clojure
(require '[martian.core :as martian]
         '[schema.core :as s])

(martian/bootstrap "https://api.org"
                   [{:route-name :create-pet
                     :path-parts ["/pets/"]
                     :method :post
                     :body-schema {:pet {:id   s/Int
                                         :name nil}}}] ; <~ Oops!
                   {:validate-handlers? true})
;; => ExceptionInfo: Invalid handlers {:handlers ({:route-name :create-pet, ... })}
```

## Idiomatic parameters

If an API has a parameter called `FooBar` it's difficult to stop that leaking into your own code — the Clojure idiom is
to use kebab-cased keywords such as `:foo-bar`. Martian maps parameters to their kebab-cased equivalents so that your
code looks neater but preserves the mapping so that the API is passed the correct parameter names:

```clojure
(require '[martian.core :as martian]
         '[schema.core :as s])

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

**Body parameters** may be supplied in three ways: with an alias, destructured or as an explicit value.

```clojure
(require '[martian.core :as martian])

;; the following three forms are equivalent
(request-for m :create-pet {:pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}})           ;; the :pet alias
(request-for m :create-pet {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"})                  ;; destructured
(request-for m :create-pet {::martian/body {:pet-id 1 :first-name "Doggy" :last-name "McDogFace"}}) ;; explicit value
```

## Parameter defaults

Martian can read `default` directives from OpenAPI/Swagger spec, or you can supply them with `schema-tools.core/default`
if [bootstrapping from data](#no-swagger-no-problem).

If you set the `use-defaults?` option to `true`, they can be seen using `explore` and merged with your param:

```clojure
(require '[martian.core :as martian]
         '[schema.core :as s]
         '[schema-tools.core :as st])

(let [m (martian/bootstrap "https://api.org"
                           [{:route-name :create-pet
                             :path-parts ["/pets/"]
                             :method :post
                             :body-schema {:pet {:id   s/Int
                                                 :name (st/default s/Str "Bryson")}}}]
                           {:use-defaults? true})]

  (martian/explore m :create-pet)
  ;; => {:summary nil, :parameters {:pet {:id Int, :name (default Str "Bryson")}}, :returns {}}

  (martian/request-for m :create-pet {:pet {:id 123}}))
  ;; => {:method :post, :url "https://api.org/pets/", :body {:id 123, :name "Bryson"}}
```

## Route name sources

By default, you need to have an "operationId" property in the OpenAPI/Swagger definition to name a corresponding route
when using `bootstrap-openapi`/`bootstrap-swagger` functions.

The `:route-name-sources` option can be used to generate route names for definitions that don't have an "operationId":

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http])

(-> (martian-http/bootstrap-swagger "https://poligon.aidevs.pl/swagger/poligon.json")
    (martian/explore))
;; WARN martian.openapi - No route name, ignoring endpoint {:url-pattern :/dane.txt, :method :get}
;; WARN martian.openapi - No route name, ignoring endpoint {:url-pattern :/verify, :method :post}
;; => []

(-> (martian-http/bootstrap-swagger "https://poligon.aidevs.pl/swagger/poligon.json"
                                    {:route-name-sources [:operationId :method+path]})
    (martian/explore))
;; => [[:get-dane-txt "Retrieve data file"] [:post-verify "Submit report"]]
```

The `:route-name-sources` is a vector of route name sources to try in order. Supported sources are:
- `:operationId` — use an "operationId" property from the definition
- `:method+path` — use a method and URL (path) pattern concatenation
- any ternary fn of URL (path) pattern, HTTP method, and definition

Note that [pedestal-api](https://github.com/oliyh/pedestal-api) auto-generates `operationId`s from given route names.

## Built-in media types

These [media types](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types) are available out of the box
and are used by Martian, e.g. for the "Content-Type" negotiation, when parsing the OpenAPI/Swagger definition, etc.,
in the following order:

1. `application/transit`
   - `application/transit+msgpack` — supported for JVM HTTP clients only!
   - `application/transit+json` — supported for _all_ target HTTP clients
   - the first one, if present, takes precedence
2. `application/edn`
   - supported for _all_ target HTTP clients
3. `application/json`
   - supported for _all_ target HTTP clients
4. `application/x-www-form-urlencoded`
   - supported for all target JVM and JS HTTP clients
   - not supported for BB-compatible HTTP clients when run with Babashka
5. `multipart/form-data`
   - only available for request encoding, but not for response coercion!
   - supported for all target JVM/BB HTTP clients except `clj-http-lite`
   - (as of now) not supported for JS HTTP clients

This is what you get when a Martian instance is bootstrapped with default options, which come with `default-encoders`.
If necessary, they can also be configured more finely [by passing options](#built-in-encoders-options).

## Response validation

Martian provides a response validation interceptor which validates the response body against the response schemas.
It is not included in the default interceptor stack, but you can include it yourself:

```clojure
(require '[martian.core :as martian]
         '[martian.clj-http :as martian-http]
         '[martian.interceptors :as i])

(martian-http/bootstrap-openapi
 "https://example-api.com"
 {:interceptors (i/inject martian-http/default-interceptors
                          (i/validate-response-body {:strict? true})
                          :before ::martian/coerce-response)})
```

The `strict?` argument defines whether any response with an undefined schema is allowed. For example, if a response
schema is defined for a `200` status code only, but the server returns a response with status code `500`, strict mode
will throw an error, while non-strict mode will allow it. The strict mode defaults to `false`.

## Testing with `martian-test`

Testing code that calls external systems can be tricky — you either build often elaborate stubs which start to become
as complex as the system you are calling, or else you ignore it all together with `(constantly true)`.

### Generative testing

Martian will assert that you provide the right parameters to the call, and the `martian-test` will return a response
generated from the response schema of the remote application. This gives you more confidence that your integration is
correct without maintenance of a stub.

The following example shows how exceptions will be thrown by bad code and how responses can be generated using the
`martian.test/respond-with-generated` function:

```clojure
(require '[martian.core :as martian]
         '[martian.httpkit :as martian-http]
         '[martian.test :as martian-test])

(let [m (-> (martian-http/bootstrap-openapi "https://pedestal-api.oliy.co.uk/swagger.json")
            (martian-test/respond-with-generated {:get-pet :random}))]

  (martian/response-for m :get-pet {})
  ;; => ExceptionInfo: Value cannot be coerced to match schema: {:id missing-required-key}

  (martian/response-for m :get-pet {:id "bad-id"})
  ;; => ExceptionInfo: Value cannot be coerced to match schema: {:id (not (integer? bad-id))}

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200, :body {:id -3, :name "EcLR"}}
```

The `martian-test` has generative interceptors that always give successful responses, always errors, or a random choice:
`martian.test/generate-success-response`, `martian.test/generate-error-response`, and `martian.test/generate-response`.

By making your application code accept a Martian instance you can inject a test instance within your tests, making
previously untestable code testable again.

### Non-generative testing

All other non-generative testing approaches and techniques, such a mocks, stubs, and spies, are also supported.

The following example shows how mock responses can be created using the `martian.test/respond-with` function:

```clojure
(require '[martian.core :as martian]
         '[martian.httpkit :as martian-http]
         '[martian.test :as martian-test])

(let [m (-> (martian-http/bootstrap-openapi "https://pedestal-api.oliy.co.uk/swagger.json")
            (martian-test/respond-with {:get-pet {:name "Fedor Mikhailovich" :type "Cat" :age 3}}))]

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200, :body {:name "Fedor Mikhailovich" :type "Cat" :age 3}}

(let [m (-> (martian-http/bootstrap-openapi "https://pedestal-api.oliy.co.uk/swagger.json")
            (martian-test/respond-with {:get-pet (fn [_request]
                                                   (let [rand-age (inc (rand-int 50))
                                                         ret-cat? (even? rand-age)]
                                                     {:name (if ret-cat? "Fedor Mikhailovich" "Doggy McDogFace")
                                                      :type (if ret-cat? "Cat" "Dog")
                                                      :age rand-age}))}))]

  (martian/response-for m :get-pet {:id 123})
  ;; => {:status 200, :body {:name "Fedor Mikhailovich" :type "Cat" :age 12}}

  (martian/response-for m :get-pet {:id 123}))
  ;; => {:status 200, :body {:name "Doggy McDogFace" :type "Dog" :age 7}}
```

More documentation is available at [martian-test](https://github.com/oliyh/martian/tree/master/test).

## Recording and playback with `martian-vcr`

The `martian-vcr` module enables Martian instances to record responses from real HTTP requests and play them back later,
allowing you to build realistic test data quickly and easily.

```clojure
(require '[martian.vcr :as vcr]
         '[martian.core :as martian]
         '[martian.clj-http :as martian-http]
         '[martian.interceptors :refer [inject]])

(def m (martian-http/bootstrap "https://foo.com/api"
                               {:interceptors (inject martian-http/default-interceptors
                                                      (vcr/record opts)
                                                      :after ::martian-http/perform-request)}))

(martian/response-for m :load-pet {:id 123})
;; the response is now recorded and stored at "test-resources/vcr/load-pet/-655390368/0.edn"
```

More documentation is available at [martian-vcr](https://github.com/oliyh/martian/tree/master/vcr).

## Custom behaviour

Martian supports a wide range of customisations — through interceptor chain, configurable encoders and media types,
HTTP client options, and parameter schema coercion matcher.

### Custom interceptors

You may wish to provide additional behaviour to requests. This can be done by providing Martian with interceptors
which behave in the same way as pedestal interceptors.

#### Global interceptors

You can add interceptors to the stack that get executed on every request when bootstrapping Martian.
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
               "https://pedestal-api.oliy.co.uk/swagger.json"
               ;; see the note on using `martian.interceptors/inject` fn below
               {:interceptors (concat [add-authentication-header request-timer]
                                      martian-http/default-interceptors)})]

        (martian/response-for m :all-pets {:id 123}))
        ;; Request to :all-pets took 38ms
        ;; => {:status 200 :body {:pets []}}
```

There is also the `martian.interceptors/inject` function that you can leverage to be more specific and descriptive when
adding a custom interceptor or replacing/removing an existing (default) one.

#### Per route interceptors

Sometimes individual routes require custom behaviour. This can be achieved by writing a global interceptor which
inspects the handler's `:route-name` and decides what to do, but a more specific option exists using `bootstrap`
and providing `:interceptors` as follows:

```clojure
(require '[martian.core :as martian]
         '[schema.core :as s])

(martian/bootstrap "https://api.org"
                   [{:route-name :load-pet
                     :path-parts ["/pets/" :id]
                     :method :get
                     :path-schema {:id s/Int}
                     :interceptors [{:name ::override-load-pet-method
                                     :enter #(assoc-in % [:request :method] :xget)}]}])
```

Alternatively you can use the helpers like `update-handler` to update a Martian created from `bootstrap-openapi`:

```clojure
(-> (martian/bootstrap-openapi "https://api.org" openapi-definition)
    (martian/update-handler :load-pet assoc :interceptors [{:name ::override-load-pet-method
                                                            :enter #(assoc-in % [:request :method] :xget)}]))
```

Interceptors provided at a per-route level are inserted into the interceptor chain at execution time by the interceptor
called `:martian.interceptors/enqueue-route-specific-interceptors`. Assuming the `martian.interceptors` ns is required
with `:as i` alias and, optionally, core ns for any target HTTP client module is required with `:as martian-http` alias,
this results in the following interceptor chain:

1. `::i/keywordize-params`
2. `::i/set-method`
3. `::i/set-url`
4. `::i/set-query-params`
5. `::i/set-body-params`
6. `::i/set-form-params`
7. `::i/set-header-params`
8. `::i/enqueue-route-specific-interceptors` — injects the following at runtime:
   1. a route-specific interceptor, e.g. the above `::override-load-pet-method`
   2. another route-specific interceptor coming after the previous one
   3. etc.
9. `::i/encode-request`, if any
10. `::i/coerce-response`, if any
11. `::martian-http/perform-request`, if any

This means your route interceptors have available to them the unencoded (non-serialised) request on `:enter` stage and
the coerced (deserialised) response on `:leave` stage. And, as with any other interceptor, you may move or provide your
own version of the `::i/enqueue-route-specific-interceptors` to change this behaviour.

### Custom coercion matcher

There is also a way to augment/override the default coercion matcher that is used by a Martian instance for parameters
coercion:

```clojure
(require '[martian.core :as martian]
         '[martian.httpkit :as martian-http])

;; adding an extra coercion instead/after the default one
(martian-http/bootstrap-openapi
  "https://pedestal-api.oliy.co.uk/swagger.json"
  {:coercion-matcher (fn [schema]
                       (or (martian/default-coercion-matcher schema)
                           (my-extra-coercion-matcher schema)))})

;; switching to some coercion matcher from 'schema-tools'
(require '[schema.core :as s]
         '[schema-tools.coerce :as stc])
(martian/bootstrap
  "https://api.org"
  [{:route-name  :create-pet
    :path-parts  ["/pets/"]
    :method      :post
    :body-schema {:pet {:PetId     s/Int
                        :FirstName s/Str
                        :LastName  s/Str}}}]
  {:coercion-matcher stc/json-coercion-matcher})
```

### Built-in encoders options

By default, the `martian.encoders/default-encoders` is configured with `{:json {:decode {:key-fn keyword}}}` options,
but you can provide custom options for the built-in media type encoders. The shape of the options map for this function
looks like this:

```clojure
{:transit {:encode <transit-encode-opts>
           :decode <transit-decode-opts>}
 :json {:encode <json-encode-opts>
        :decode <json-decode-opts>}
 :edn {:encode <edn-encode-opts>
       :decode <edn-decode-opts>}}
```

Check out the `martian.encoders` ns for all supported Transit, JSON, and EDN encoding/decoding options.

Also, for your convenience, this namespace provides constructor functions for the encoders of all built-in media types:
`transit-encoder`, `json-encoder`, `edn-encoder`, and `form-encoder`. You can use them directly to create a customized
encoder instance for a specific media type. For example, you can pass an `:as` param with a different raw type, e.g.:

```clojure
;; Supported raw types: `:string` (default), `:stream`, `:byte-array`.
;; The last 2 are JVM/BB specific and won't work with JS HTTP clients.

(transit-encoder :json {:encode ..., :decode ...} :as :byte-array)

(edn-encoder {:encode ..., :decode ...} :as :stream)

(json-encoder {:encode ..., :decode ...} :as :stream)
```

Sometimes you might find it easier to patch a built-in Martian encoder in place, like this:

```clojure
(def my-encoders
  (update (encoders/default-encoders)
          "application/transit+json" assoc :as :stream))
;; now the transit decoder will expect an InputStream from the HTTP client
```

Pass `my-encoders` to the function that bootstraps a Martian instance, as [shown below](#custom-media-types).

### Custom media types

Martian allows you to add support for custom media types in addition to [the default ones](#built-in-media-types). They
can be added independently for request and response encoders. Here's how it can be achieved in practice:

```clojure
(require '[clojure.string :refer :all]
         '[martian.core :as martian]
         '[martian.encoders :as encoders]
         '[martian.httpkit :as martian-http]
         '[martian.interceptors :as i])

(def magical-encoder
  {;; a unary fn of request `:body`, Str -> Str
   :encode upper-case
   ;; a unary fn of response `:body`, Str -> Str
   :decode lower-case
   ;; tells HTTP client what raw type to provide
   ;; one of `:string`, `:stream`, `:byte-array`
   :as :string})

(let [request-encoders (assoc martian-http/default-request-encoders
                         "application/magical" magical-encoder)
      response-encoders (assoc martian-http/default-response-encoders
                          "application/magical" magical-encoder)]
  
  ;; provide via `:request-encoders`/`:response-encoders` opts
  (martian-http/bootstrap-openapi
    "https://example-api.com"
    {:request-encoders request-encoders
     :response-encoders response-encoders})
  
  ;; or by rebuilding a complete interceptor chain from scratch
  (martian-http/bootstrap-openapi
   "https://example-api.com"
   {:interceptors (conj martian/default-interceptors
                        (i/encode-request request-encoders)
                        (i/coerce-response response-encoders martian-http/response-coerce-opts)
                        martian-http/perform-request)})
  
  ;; or by leveraging the `martian.interceptors/inject` fn
  (def my-encode-request (i/encode-request request-encoders))
  (def my-coerce-response (i/coerce-response response-encoders martian-http/response-coerce-opts))
  (martian-http/bootstrap-openapi
    "https://example-api.com"
    {:interceptors (-> martian-http/default-interceptors
                       (i/inject my-encode-request :replace ::martian/encode-request)
                       (i/inject my-coerce-response :replace ::martian/coerce-response))}))
```

### HTTP client-specific options

Similar to what was described for request/response encoders in the [Custom media types](#custom-media-types) section,
there may be other Martian bootstrap options that customize HTTP client-specific behavior.

Async-compatible HTTP clients, such as `hato` and `babashka/http-client`, support the `async?` option (false by default)
which switches from using the `::martian-http/perform-request` interceptor to `::martian-http/perform-request-async`.

HTTP clients with rich "Content-Type"-based response auto-coercion capabilities, such as `clj-http` and `hato`, support
the `use-client-output-coercion?` (false by default) which allows to skip Martian response decoding for some media types
that the client is known to be able to auto-coerce itself.

For a complete list of available options, check out the `supported-custom-opts` var in the HTTP client's module core ns.

## Development mode

When Martian is bootstrapped it closes over the route definitions and any options you provide, returning an _immutable_
instance. This can hamper REPL development when you wish to rapidly iterate on your Martian definition, so all Martian
API calls also accept a function or a var that returns the instance instead:

```clojure
(martian/url-for (fn [] (martian/bootstrap ... )) :load-pet {:id 123}) ;; => "https://api.com/pets/123"
```

## Java

Martian can be used from Java code as follows:

```java
import martian.Martian;
import java.util.Map;
import java.util.HashMap;

Map<String, Object> swaggerSpec = { ... };
Martian martian = new Martian("https://pedestal-api.oliy.co.uk", swaggerSpec);

martian.urlFor("get-pet", new HashMap<String, Object> {{ put("id", 123); }});

// => https://pedestal-api.oliy.co.uk/pets/123
```

## Caveats

- Martian does not yet cover every intricacy of JSON schema when parsing OpenAPI/Swagger specs, and as such it may not
  transmit data that it decides does not conform to the schema it has derived
  - The main examples currently are `anyOf`, `allOf` and `oneOf`

## Development

[![Circle CI](https://circleci.com/gh/oliyh/martian.svg?style=svg)](https://circleci.com/gh/oliyh/martian)

Use `cider-jack-in-clj` or `cider-jack-in-clj&cljs` to start Clojure (and CLJS where appropriate) REPLs for development.

You may need to `lein install` first if you're working with/in a module that depends on another module.

## Issues and features

Please feel free to raise issues on GitHub or send pull requests.

## Acknowledgements

Martian uses [tripod](https://github.com/frankiesardo/tripod) for routing, inspired by [pedestal](https://github.com/pedestal/pedestal).
