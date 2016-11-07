# martian-test
`martian-test` is a library which uses your
Swagger definition to validate your calls and return realistic responses without requiring a real
HTTP server. This lets you take advantage of generative testing to test thousands of permutations
and help you build robust interfaces with other systems.


## Usage

Let's consider a somewhat naive user lookup function:

```clojure
(defn find-user [m user-name]
  (let [user (:body (martian/response-for m :load-user {:user-name user-name}))]
    {:name (or (:name user) "Guest")
     :write-access? (not (:read-only user))}))
```

The inclusion of the HTTP request makes it tricky to test - you don't want to have to write a stub
HTTP server just for this. Of course, you could rewrite the function to compose it in a better way,
and unit test the logic separately from the HTTP call. You might write some code that builds the
sort of response that you can pass in to your function to test it, but then your response-building
code starts to look like a stub HTTP server too! You also run the risk of any hardcoded stub
response drifting away from the real response the server would return, making your tests irrelevant.

Martian can use the production definition of the server's API to generate the response, requiring no
stub code and providing responses that are always up to date. We can write the tests like this:

```clojure
(require '[martian.core :as martian]
          [martian.test :as martian-test])

(deftest find-user-test
  (testing "happy path works"
    (let [m (-> (martian/bootstrap-swagger "https://api.com" user-api-swagger-definition)
                (martian-test/respond-with :success))]
          user (find-user m "abc")]
      (is (not= "Guest" (:name user)))
      (is (instance? Boolean (:write-access? user)))))

  (testing "guest access works"
    (let [m (-> (martian/bootstrap-swagger "https://api.com" user-api-swagger-definition)
                (martian-test/respond-with :error))]
          user (find-user m "abc")]
      (is (= "Guest" (:name user)))
      (is (false? (:write-access? user))))))
```

As always, you can write your own interceptors with whatever behaviour you want, building on top
of what Martian provides but always working at the interface level where you want to be.

Furthermore you can use martian-test's response generators to write generative tests and explore
all possible behaviour in the following way:

```clojure
(require '[clojure.test.check :as tc]
         '[clojure.test.check.generators :as gen]
         '[clojure.test.check.properties :as prop]
         '[clojure.test.check.clojure-test :as tct])


(deftest find-user-generative-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition)
        p (prop/for-all [response (martian-test/response-generator m :load-user)]

                        (let [user (find-user (martian-test/constantly-respond m response))]
                          (if (= 200 (:status response))
                            (and (= (:name response) (:name user))
                                 (= (not (:read-only response)) (:write-access? user)))

                            (and (= "Guest" (:name user))
                                 (false? (:write-access? user))))))]

    (tct/assert-check (tc/quick-check 100 p))))
```

The inherent variability in the sort of responses you can get from remote HTTP calls makes it
a perfect fit for generative testing, giving you confidence that your application will behave
correctly no matter what response the remote API throws at it.
