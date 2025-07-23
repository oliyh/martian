# martian-vcr

The `martian-vcr` is a library which allows you to easily record and play back HTTP requests made through Martian.

## Usage

You can use `martian-vcr` in two ways: recording and playback.

Recording captures responses from HTTP requests while playing back returns the recorded responses instead of making real
requests.

### Record

Take a martian definition and bootstrap it with the extra interceptor `martian.vcr/record`:

```clojure
(require '[martian.core :as m])
(require '[martian.interceptors :refer [inject]])
(require '[martian.clj-http :as http])
(require '[martian.vcr :as vcr])

(def routes [{:route-name :load-pet
              :path-parts ["/pets/" :id]
              :method :get
              :path-schema {:id s/Int}}])

;; options for recording/playback - see below
(def opts {:store {:kind :file
                   :root-dir "test-resources/vcr"
                   :pprint? true}})

(def m (http/bootstrap "https://foo.com/api"
                       {:interceptors (inject http/default-interceptors
                                              (vcr/record opts)
                                              :before (:name http/perform-request))}))

(m/response-for m :load-pet {:id 123})
;; the response is now recorded and stored at "test-resources/vcr/load-pet/-655390368/0.edn"
```

You can populate the directory — use a different one for different test suites — by just making requests as you normally
would.

If you need to strip out any sensitive or uninteresting information from the response, simply add another interceptor
between `::http/perform-request` and `::vcr/record` to do this.

### Playback

Given a directory of responses populated by the record interceptor, you can now use the `playback` interceptor to return
these responses without making HTTP requests — perfect for a test suite or working offline.

```clojure
(def m (http/bootstrap "https://foo.com/api"
                       {:interceptors (inject http/default-interceptors
                                              (vcr/playback opts)
                                              :replace (:name http/perform-request))}))

(m/response-for m :load-pet {:id 123})
;; the response is read from "test-resources/vcr/load-pet/-655390368/0.edn" and returned
```

### Options

Options are supplied as a map, like this:

```clojure
{:store {:kind :file
         :root-dir "target"
         :pprint? true}
 :on-missing-response :generate-404
 :extra-requests :repeat-last}
```

The options available are:

#### Store

The `:store` option can be one of the following:

##### File store

For Clojure only:

```clojure
{:kind :file
 :root-dir "target" ;; where the response files are written
 :pprint? true ;; whether to pprint the files (uses fipp)
}
```

Note that for `application/transit+***` content-types you may need to consider how Transit objects serialise via reader
macros or similar.

##### Atom store

For Clojure and ClojureScript:

```clojure
{:kind :atom
 :store (atom {}) ;; where the responses are assoced
}
```

#### Missing response behaviour

The `:on-missing-response` allows you to choose between the following behaviours when a response is not in the store
during playback:

- not specified — the default behaviour is to do nothing — the context is passed through unchanged; this can be useful
                  during playback to allow the real request to be made and recorded as a way of augmenting your store;
- `:throw-error` — an error is thrown which can be handled by another interceptor;
- `:generate-404` — a barebones 404 response is returned.

#### Extra requests

VCR will play responses to you in the order they were recorded, enabling playback of mutable webservices (e.g. polling
endpoints). If you make more requests than there are recorded responses, you can choose the behaviour by setting the
`:extra-requests` option:

- `:repeat-last` — the last recorded response will be repeated;
- `:cycle` — the responses will be cycled.
