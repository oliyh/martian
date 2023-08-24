(ns reveal.slides)

(def welcome
  [:section
   [:h1 "Martian"]
   [:h5 "Abstracting HTTP since 2016"]
   [:aside.notes
    [:ul
     [:li "Hello"]
     [:li "Oliver Hine, a Clojure developer since 2012"]
     [:li "Worked on various projects with Juxt over the years"]
     [:li "Martian is a library I started in 2016 back when inflation was low, house prices were reasonable and corona was a kind of beer"]]]])

(def genesis
  [:section
   [:h1 "Genesis"]
   [:aside.notes
    [:ul
     [:li "Writing a UI for a risk system that traders used to track their profit, loss and risk"]
     [:li "Lots of data to be sourced from lots of endpoints on lots of different services, all via HTTP, to materialise a view of the world"]
     [:li "The parameters we provided and the shape of the data varied a lot between these services, the business domain was complex"]
     [:li "At the same time a lot of HTTP domain language, like hosts, ports, verbs, serialisation, authentication, logging and metrics was leaking into our business code"]]]])

(def in-a-nutshell
  [:section
   [:h1 "In a nutshell"]
   [:p "Martian uses a description of an HTTP API's functionality to provide a simple, functional, idiomatic interface that is agnostic of HTTP"]
   [:aside.notes
    [:ul [:li "(read the text)"]
     [:li "It's important to distinguish here the difference between what you can do with an API - its functionality - and how you invoke it"]
     [:li "An API specification can be implemented in many different ways - a native client library, an HTTP server, a database driver"]
     [:li "An API can be implemented using just HTTP terminology - urls, methods, headers - but other implementations like SOAP and GraphQL use HTTP more as a transport for their own protocol"]
     [:li "Regardless of how many ways your API can be called, the functionality remains the same, and is what actually delivers value"]]]])

(def why-use-http
  [:section
   [:h1 "What's great about HTTP?"]
   [:aside.notes
    [:ul
     [:li "HTTP is (mostly) simple to understand, generally human-readable"]
     [:li "Webservers are easy to build and run"]
     [:li "It's broad enough to make most things achievable but narrow enough that it's easy to reason about"]
     [:li "Ecosystem is very powerful - browsers, javascript, stable specifications"]]]])

(def complecting
  [:section
   [:h1 "Complecting"] ;; todo better title?
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
     [:li "There are lots of bits of HTTP that you might want to hide away from your business domain"]
     [:li "Here we've got to know the method, how to build a url, what parameters are query params, body params"]
     [:li "How the request is encoded, how the response is encoded"]
     [:li "At least we've hidden it in this function and the function arguments still reflect the business domain"]]]])

(def complecplecting
  [:section
   [:h1 "Complecplecting"]
   [:pre [:code {:data-trim true :data-line-numbers "1-8|2|3|8|1"}
"(defn create-pet [metrics host port creds species name age]
  (timing metrics \"create-pet\"
    (http/post (format \"https://%s:%s/create-pet/%s\" host port species)
               {:as :json
                :query-params {:age age}
                :body (json/encode {:name name))
                :headers {\"Content-Type\" \"application/json\"
                          \"Authorization\" (str \"Token \" creds)}})))"          ]]
   [:aside.notes
    [:ul [:li "But sometimes despite our good intentions these things might leak out"]
     [:li "Here host and port have made it into our function arguments"]
     [:li "Also because this is going over the network to another machine we need to authenticate with it so we send in credentials"]
     [:li "Going over the network means things can be delayed, go wrong, the other machine might be busy"]
     [:li "So we add metrics and logging to help track that, these are implicit to it being HTTP call"]
     [:li "But now our function has more non-functional arguments than functional ones! The signal to noise ratio here is very low"]]]])

(def server
  [:section
   [:h1 "Declarative servers"]
   [:pre [:code {:data-trim true}
          "(defhandler create-pet
  {:parameters {:path-params  {:species Species}
                :query-params {:age Age}
                :body-params  {:name Name}}
   :responses {201 {:body {:id Id}}}}
   ...)"]]
   [:aside.notes
    [:ul
     [:li "Servers and clients need a symmetric understanding of the API - both what it can do, and how it can be invoked"]
     [:li "Technologies like OpenAPI (previously Swagger), and libraries in Clojure like ring-swagger and pedestal-api let us describe both these things mostly separately"]
     [:li "We leave things like authentication and serialisation to middleware so that our request handlers can focus on the actual functionality"]
     [:li "Why can't the client side be more like this?"]]]])



(def martian-one-liner
  [:section
   [:h1 "Martian"]
   [:pre [:code {:data-trim true}
          "(let [m (m/bootstrap-openapi \"\")]
  (m/response-for m :create-pet {:name \"Charlie\"
                                     :species \"Dog\"
                                     :age 3}))"]]
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


(defn all
  []
  [welcome
   genesis
   in-a-nutshell
   why-use-http

   ;; what goes wrong
   complecting
   complecplecting

   server
   martian-one-liner
   goals

   ;; things you can do with core martian
   ;; - coercion / validation
   ;; - idiomatic params
   ;; - add / remove interceptors
   ;; - any http library


   ;; testing etc

   ;; expansion to other http libraries, babashka, vcr etc

   ;; interceptors

   ;; adoption and community support
   ])

;; todo
;; - picture for first slide
