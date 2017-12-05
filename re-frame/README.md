# martian-re-frame

Martian has always supported Clojurescript with the `martian-cljs-http` library, but users of re-frame
may have had to write a few boilerplate event and fx handlers to perform http requests as effects, as encouraged
by re-frame.

This library provides the basic bindings to use martian seamlessly with re-frame.

If you are familiar with both martian and re-frame the following code should show you the simplification
that martian can bring:

```clj
(require '[martian.re-frame :as martian])
(require '[re-frame.core :as re-frame])

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-db
 ::create-pet-success
 interceptors
 (fn [db [{:keys [body]} operation-id params]]
   (assoc db :pet-id (:id body))))

(re-frame/reg-event-db
 ::http-failure
 interceptors
 (fn [db [response-or-error operation-id params]]
   (update db :errors conj [operation-id response-or-error])))

(martian/init "http://pedestal-api.herokuapp.com/swagger.json")

(re-frame/dispatch [::martian/request             ;; event for performing an http request
                    :create-pet               ;; the route name to call
                    {:name "Doggy McDogFace"  ;; data to send to the endpoint
                     :type "Dog"
                     :age 3}
                    ::create-pet-success      ;; event to dispatch on success
                    ::http-failure            ;; event to dispatch on failure
                    ])
```
