(ns reveal.slides)

(def welcome
  [:section
   [:h1 "Martian"]
   [:h5 "Abstracting HTTP since 2016"]
   [:aside.notes
    [:ul [:li "Some notes"]]]])

(def in-a-nutshell
  [:section
   [:h1 "In a nutshell"]
   [:p "Martian uses a description of an HTTP API to provide a simple, functional, idiomatic interface"]
   [:aside.notes
    [:ul [:li "Some notes"]]]])

(def whats-wrong-with-http
  [:section
   [:h1 "What's wrong with HTTP?"]
   [:aside.notes
    [:ul [:li "Some notes"]]]])

(def complecting
  [:section
   [:h1 "Complecting"]
   [:pre [:code {:data-trim true :data-line-numbers "1-6|2|3|4-5|6"}
          "(defn create-pet [species name age]
  (http/post (format \"https://api.io/pets/%s\" species)
             {:query-params {:age age}
              :body (json/encode {:name name))
              :headers {\"Content-Type\" \"application/json\"}
              :as :json}))
"]]
   [:aside.notes
    [:ul [:li "Some notes"]]]])

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
    [:ul [:li "Some notes"]]]])




(defn all
  []
  [welcome
   in-a-nutshell

   whats-wrong-with-http
   complecting
   complecplecting
   ;; testing etc

   ;; expansion

   ;; interceptors

   ;; adoption and community support
   ])
