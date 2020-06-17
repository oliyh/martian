# martian-vcr
`martian-vcr` is a library which allows you to easily record and play back HTTP requests made through martian.

## Usage

You can use martian-vcr in two ways: recording and playback. Recording captures responses from HTTP requests while playback returns

### Record

Take a martian definition and bootstrap it with the extra interceptor `martian.vcr/record`:

```clj
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

(def m (m/bootstrap "https://foo.com/api"
                    {:interceptors (inject http/default-interceptors
                                           (vcr/record opts)
                                           :after http/perform-request)}))

(m/response-for m :load-pet {:id 123})
;; the response is now stored at test-resources/vcr/load-pet/-655390368.edn
```

You can populate the directory - use a different one for different test suites - by just making requests as you normally would.

If you need to strip out any sensitive or uninteresting information from the response, simply add another interceptor between `perform-request` and `record` to do this.

### Playback

Given a directory of responses populated by the record interceptor, you can now use the `playback` interceptor to return these responses without making HTTP requests - perfect for a test suite
or working offline.

```clj
(def m (m/bootstrap "https://foo.com/api"
                    {:interceptors (inject http/default-interceptors
                                           (vcr/playback opts)
                                           :before http/perform-request)}))

(m/response-for m :load-pet {:id 123})
;; the response is read from test-resources/vcr/load-pet/-655390368.edn and returned
```

### Options

Options are supplied as a map, like this:

```clj
{:store {:kind :file
         :root-dir "target"
         :pprint? true}
 :on-missing-response :generate-404}
 ```

The options available are:

#### Store

The `:store` option can be one of the following:

##### File store

For Clojure only:

 ```clj
{:kind :file
 :root-dir "target" ;; where the response files are written
 :pprint? ;; whether to pprint the files (uses fipp)
}
 ```

 ##### Atom store

 For Clojure and Clojurescript:

 ```clj
{:kind :atom
 :store (atom {}) ;; where the reponses are assoced
}
```

#### Missing response behaviour

The `:on-missing-response` allows you to choose between the following behaviours when a response is not in the store during playback:

#### default
If you do not specify, the default behaviour is to do nothing - the context is passed through unchanged.
This can be useful during playback to allow the real request to be made and recorded as a way of augmenting your store.

#### throw-error
An error is thrown which can be handled by another interceptor

#### generate-404
A barebones 404 response is returned
