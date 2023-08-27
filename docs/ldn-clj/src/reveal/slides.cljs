(ns reveal.slides)

(def welcome
  [:section
   [:h1 "Martian"]
   [:h5 "Abstracting HTTP since 2016"]
   [:img {:src "img/perseverance.jpg"}]
   [:aside.notes
    [:ul
     [:li "Hello"]
     [:li "Oliver Hine, a Clojure developer since 2012"]
     [:li "Worked on various projects with Juxt over the years"]
     [:li "Martian is a library I started in 2016 back when inflation was low, house prices were reasonable and corona was a kind of beer"]]]])

(def genesis
  [:section
   [:h1 "Genesis"]
   [:img {:src "img/mars.jpeg"}]
   [:aside.notes
    [:ul
     [:li "Writing a UI for a risk system that traders used to track their profit, loss and risk"]
     [:li "Lots of data from lots of endpoints on lots of different services, all via HTTP, to materialise a view of the world"]
     [:li "The parameters and the shape of the data varied a lot between these services, the business domain was complex"]
     [:li "A lot of HTTP domain language, like hosts, ports, verbs, serialisation, authentication, logging and metrics was leaking into business code"]]]])

(def in-a-nutshell
  [:section
   [:h1 "In a nutshell"]
   [:p "Martian uses descriptions of HTTP APIs"
    [:br]
    [:em "to provide"]
    [:br]
    "simple, functional interfaces agnostic of HTTP"]
   [:aside.notes
    [:ul [:li "(read the text)"]
     [:li "Important to distinguish the difference between what you can do with an API - its functionality - and how you invoke it"]
     [:li "An API specification can be implemented in many ways - native client library, HTTP server, database driver"]
     [:li "Can be implemented using just HTTP terminology - urls, methods, headers - but other implementations like SOAP and GraphQL use HTTP more as a transport for their own protocol"]
     [:li "Regardless of how many ways your API can be called, the functionality remains the same, and is what actually delivers value"]]]])

(def why-use-http
  [:section
   [:h1 "♥ HTTP"]
   [:aside.notes
    [:ul
     [:li "HTTP is (mostly) simple to understand, generally human-readable"]
     [:li "Webservers are easy to build and run"]
     [:li "It's broad enough to make most things achievable but narrow enough that it's easy to reason about"]
     [:li "Ecosystem is very powerful - browsers, javascript, stable specifications"]]]])

(def the-bad
  [:section
   [:h1 "The bad"]
   [:pre [:code {:data-trim true :data-line-numbers "1-6|2|3|4-5|6"}
          "(defn create-pet [species name age]
  (http/post (format \"https://api.io/pets/%s\" species)
             {:query-params {:age age}
              :body (json/encode {:name name))
              :headers {\"Content-Type\" \"application/json\"}
              :as :json}))
"]]
   [:aside.notes
    [:ul
     [:li "Lots of HTTP that you don't want in business domain"]
     [:li "Need to know the method, how to build a url, what parameters are query params, body params"]
     [:li "How the request is encoded, how the response is encoded"]
     [:li "At least we've hidden it in this function and the function arguments still reflect the business domain"]]]])

(def the-ugly
  [:section
   [:h1 "The ugly"]
   [:pre [:code {:data-trim true :data-line-numbers "1-9|4|9|2|1"}
"(defn create-pet [metrics host port creds species name age]
  (timing metrics \"create-pet\"
    (http/post
      (format \"https://%s:%s/create-pet/%s\" host port species)
      {:as :json
       :query-params {:age age}
       :body (json/encode {:name name))
       :headers {\"Content-Type\" \"application/json\"
                 \"Authorization\" (str \"Token \" creds)}})))"]]
   [:aside.notes
    [:ul
     [:li "But sometimes despite our good intentions these things might leak out"]
     [:li "Here host and port have made it into our function arguments"]
     [:li "Also because this is going over the network to another machine we need to authenticate with it so we send in credentials"]
     [:li "Going over the network means things can be delayed, go wrong, the other machine might be busy"]
     [:li "So we add metrics and logging to help track that, implicit cost of it being HTTP"]
     [:li "Now our function has more non-functional arguments than functional ones! The signal to noise ratio very low"]]]])

(def server
  [:section
   [:h1 "Declarative servers"]
   [:pre [:code {:data-trim true}
          "(defhandler create-pet
  {:parameters {:path-params  {:species Species}
                :query-params {:age Age}
                :body-params  {:name Name}}
   :responses {201 {:body {:id Id}}}}
   (fn [{:keys [params]}]
     (let [{:keys [name species age]} params]
       ...)"]]
   [:aside.notes
    [:ul
     [:li "Servers and clients need a symmetric understanding of the API - both what it can do, and how it can be invoked"]
     [:li "Technologies like OpenAPI (previously Swagger), and libraries in Clojure like ring-swagger and pedestal-api let us describe both these things mostly separately"]
     [:li "We leave things like authentication and serialisation to middleware so that our request handlers can focus on the actual functionality"]
     [:li "Why can't the client side be more like this?"]]]])

(def martian-one-liner
  [:section
   [:h1 "One liner"]
   [:pre [:code {:data-trim true :data-line-numbers "1|3-5"}
          "(def m (m/bootstrap-openapi \"https://api.io/swagger.json\"))

(m/response-for m :create-pet {:name \"Charlie\"
                               :species \"Dog\"
                               :age 3})"]]
   [:aside.notes
    [:ul
     [:li "Martian uses a declaration of an API to build a machine that takes care of all the incidental HTTPness"]
     [:li "It leaves you with a purely functional API that speaks the language of your business domain"]
     [:li "It maps your domain into the HTTP transport for you"]
     [:li "This reduces cognitive overhead and keeps your code concise"]
     [:li "It also ensures the way you do HTTP is consistent across all endpoints and APIs that you call"]
     [:li "Interestingly it also allows refactoring of the HTTPness on the server without your code breaking"]
     [:li "For example, a POST could change to a PUT, the url could be changed, a parameter could be moved from query to body without your code changing"]]]])

(def goals
  [:section
   [:h1 "Goals"]
   [:ul
    [:li "Hide HTTPness from point of call"]
    [:li "Understand OpenAPI"]
    [:li "Support custom behaviour"]]
   [:aside.notes
    [:ul
     [:li "Hide HTTPness from the point of calling the API"]
     [:li "Understand OpenAPI / Swagger descriptions, i.e. support declarative APIs"]
     [:li "Support an internal description for APIs without OpenAPI"]
     [:li "Should work well out of the box but be flexible enough for the user to add their own auth, logging etc"]]]])


(def coercion-and-validation
  [:section
   [:h1 "Coercion & validation"]
   [:pre [:code {:data-trim true :data-line-numbers "1-2|4-5"}
"
(m/response-for m :create-pet {:name \"Charlie\"
                               :age \"3\"})

;; => ExceptionInfo Value cannot be coerced to match schema:
;;    {:species missing-required-key}"]]

   [:aside.notes
    [:ul
     [:li "Martian is implemented using plumatic schema - old but good library"]
     [:li "Can take care of simple coercion for you"]
     [:li "Can throw errors when you have bad data"]
     [:li "Gives you good local error messages instead of perhaps hard to understand remote error codes"]]]])


(def interceptors
  [:section
   [:h1 "Interceptors"]
   [:img {:src "img/interceptors.svg"
          :style "background-color: #eee; padding: 1rem;"}]
   [:aside.notes
    [:ul
     [:li "Martian exposes almost all its code as interceptors"]
     [:li "Interceptors are functions that are chained together to collaborate on building up a request"]
     [:li "Each one has a separate job like forming the URL, building headers, encoding the body etc"]
     [:li "After the request the response is handled by the same chain, in a 'leave' phase"]
     [:li "The user can add their own interceptors, remove others, reorder them - even at run time"]]]])

(def your-own-interceptor
  [:section
   [:h1 "Authentication"]
   [:pre [:code {:data-trim true}
          "(def authentication
  {:name ::authentication
   :enter (fn [ctx]
            (assoc-in ctx [:request :headers \"Authorization\"]
                          \"Token 12456abc\"))})"]]
   [:aside.notes
    [:ul
     [:li "Your own interceptors might be for authentication, metrics, logging etc"]
     [:li "Interceptors are the best way of allowing users to extend and enhance your library"]
     [:li "It has allowed martian to stay clean, minimal and true to its goals"]
     [:li "Yet still be very flexible"]]]])

(def insert-interceptor
  [:section
   [:h1 "Intercept!"]
   [:img {:src "img/insert-interceptor.svg"
          :style "background-color: #eee; padding: 1rem;"}]
   [:aside.notes
    [:ul
     [:li "The whole call stack is exposed as data for you to manipulate at will"]]]])

(def testing
  [:section
   [:h1 "Testing"]
   [:pre [:code {:data-trim true} "todo"]]
   [:aside.notes
    [:ul
     [:li "Mocks or stubs generally only cover specific scenarios that you write a test for"]
     [:li "They can drift away from the real API's functionality"]
     [:li "HTTPness will again try to infiltrate your code, obscuring the intention of the test"]]]])

(def testing-better
  [:section
   [:h1 "Testing better"]
   [:img {:src "img/test.svg"
          :style "background-color: #eee; padding: 1rem;"}]
   [:aside.notes
    [:ul
     [:li "Libraries you use in your source code should keep your code testable"]
     [:li "Martian goes further and enhances your tests with its core behaviour and testing library"]
     [:li "Request validation means you know if you are producing incorrect requests without having to make them, like an assertive mock server"]
     [:li "It uses the same API definition as your production code, so it's always up-to-date"]
     [:li "martian-test creates a mock server that you can control that hides the HTTPness"]
     [:li "It even supports generative testing by generating all possible responses"]]]])

(def vcr
  [:section
   [:h1 "VCR"]
   [:img {:src "img/vcr.jpg"}]
   [:aside.notes
    [:ul
     [:li "Another martian library is called VCR, for all you young people a VCR is like the record button on your phone"]
     [:li "It is an interceptor that can be injected into your martian instance and record all the outgoing requests and responses"]
     [:li "Another interceptor can be used to play back the server responses"]
     [:li "This can build a complete stub server for you with real data"]
     [:li "Useful for functional tests, load testing, data analysis"]]]])

(def http-libraries
  [:section
   [:h1 "HTTP libraries"]
   [:div
    (for [lib ["clj-http" "clj-http-lite" "httpkit" "hato" "cljs-http" "cljs-http-promise" "babashka"]]
      [:div {:style "display: inline-block; width: calc(50% - 2rem); margin: 1rem; border: 2px solid white;"} lib])]
   [:aside.notes
    [:ul
     [:li "If you're not sold yet you might be thinking that martian will tie you into some HTTP library you don't want to use"]
     [:li "The answer is no, supporting any HTTP library is usually just another interceptor at the end of the chain"]
     [:li "Martian supports seven HTTP libraries, helpfully all based on the excellent ring specification"]
     [:li "In one project we started with clj-http, moved to httpkit for speed but suffered with SSL, then moved to hato with minimal fuss"]]]])

(def community
  [:section
   [:h1 "Community"]
   [:img {:src "img/contributors.svg"}]
   [:aside.notes
    [:ul
     [:li "Martian has had code contributions from 24 amazing people (myself included of course)"]
     [:li "These included new http libraries like babashka, support for Open API v3 and much more"]
     [:li "Over the years my projects, roles and teams have changed and I haven't always had the time I'd like for open source"]
     [:li "Keeping martian simple and well-tested has hopefully encouraged people to contribute"]]]])

(def the-future
  [:section
   [:h1 "The future"]
   [:img {:src "img/delorean.webp"}]
   [:aside.notes
    [:ul
     [:li "The library is stable, but ideas and requests still trickle in"]
     [:li "After schema which was fairly universal came clojure.spec and malli, resulting in a bit of a schism"]
     [:li "Perhaps these could be pluggable, although it would take a lot of work"]
     [:li "Even today it feels that not many APIs on the internet seem to use OpenAPI, so perhaps there is still growth"]]]])

(def closing-thoughts ;; todo
  [:section
   [:h1 "Closing thoughts"]
   [:img {:src "img/the-martian.webp"}]
   [:aside.notes
    [:ul
     [:li ""]]]])

(def questions ;; todo
  [:section
   [:h1 "Questions?"]
   [:img {:src "img/github.png" :style "width: 30%; display: block; margin: 0 auto;"}]
   [:a {:href "https://github.com/oliyh/martian"}
    "oliyh/martian"]
   [:aside.notes
    [:ul
     [:li "Thanks for listening, any questions?"]]]])


(defn all
  []
  [welcome
   genesis
   in-a-nutshell
   why-use-http

   the-bad
   the-ugly

   server
   martian-one-liner
   goals

   coercion-and-validation
   interceptors
   your-own-interceptor
   insert-interceptor

   testing
   testing-better

   vcr
   http-libraries

   community

   the-future
   closing-thoughts
   questions
   ])

;; todo
;; - split some slides up?
;; make code align nicely
