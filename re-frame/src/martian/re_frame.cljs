(ns martian.re-frame
  (:require [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.cljs-http :as martian-http]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- ->event [handler & args]
  (into (if (vector? handler) handler [handler]) args))

(re-frame/reg-fx
 ::request
 (fn [[m operation-id params on-success on-failure]]
   (go
     (try
       (if-let [response-chan (martian/response-for m operation-id params)]
         (let [{:keys [error-code] :as response} (<! response-chan)]
           (if (= :no-error error-code)
             (re-frame/dispatch (->event on-success response operation-id params))
             (re-frame/dispatch (->event on-failure response operation-id params))))
         (re-frame/dispatch (->event on-failure :unknown-route operation-id params)))
       (catch js/Error e
         (re-frame/dispatch (->event on-failure e operation-id params)))
       (finally
         (re-frame/dispatch [::on-complete [operation-id params on-success on-failure]]))))))

(re-frame/reg-event-db
 ::init
 (fn [db [_ martian]]
   (assoc db ::martian {:m martian
                        :pending #{}})))

(defn instance [db & _]
  (get-in db [::martian :m]))

(re-frame/reg-event-db
 ::on-complete
 (fn [db [_ req]]
   (update-in db [::martian :pending] disj req)))

(defn- do-request [{:keys [db]} [_ operation-id params on-success on-failure]]
  (let [request-key [operation-id params on-success on-failure]]
    (if (contains? (get-in db [::martian :pending]) request-key)
      {:db db}
      {:db (update-in db [::martian :pending] conj request-key)
       ::request [(instance db) operation-id params on-success on-failure]})))

;; deprecated, use ::request instead
(re-frame/reg-event-fx :http/request do-request)

(re-frame/reg-event-fx ::request do-request)

(re-frame/reg-sub
 ::instance
 instance)

(re-frame/reg-sub
 ::pending-requests
 (fn [db]
   (get-in db [::martian :pending])))

(defn init [swagger-url & [params]]
  (go (let [martian (<! (martian-http/bootstrap-swagger swagger-url params))]
        (re-frame/dispatch-sync [::init martian]))))
