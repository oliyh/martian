(ns martian.re-frame
  (:require [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.cljs-http :as martian-http]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(re-frame/reg-fx
 ::request
 (fn [[m operation-id params on-success on-failure]]
   (go
     (if-let [response-chan (martian/response-for m operation-id params)]
       (let [{:keys [error-code] :as response} (<! response-chan)]
         (if (= :no-error error-code)
           (re-frame/dispatch [on-success response operation-id params])
           (re-frame/dispatch [on-failure response operation-id params])))
       (re-frame/dispatch [on-failure :unknown-route operation-id params])))))

(re-frame/reg-event-db
 ::init
 (fn [db [_ martian]]
   (assoc db ::martian martian)))

(re-frame/reg-event-fx
 :http/request
 (fn [{:keys [db]} [_ operation-id params on-success on-failure]]
   {::request [(::martian db) operation-id params on-success on-failure]}))

(defn init [swagger-url]
  (go (let [martian (<! (martian-http/bootstrap-swagger swagger-url))]
        (re-frame/dispatch-sync [::init martian]))))
