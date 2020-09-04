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
 (fn [[m instance-id operation-id params on-success on-failure]]
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
         (re-frame/dispatch [::on-complete instance-id [operation-id params on-success on-failure]]))))))

(re-frame/reg-event-fx
 ::init
 (fn [{:keys [db]} [_ martian instance-id]]
   (let [instance-id (or instance-id ::default-id)]
     (merge
      {:db (assoc-in db [::martian instance-id] {:m martian
                                                 :pending #{}})}
      (when-let [queue (get-in db [::martian instance-id :queue])]
        {:dispatch-n queue})))))

(defn instance
  ([db]
   (instance db []))
  ([db [_ instance-id]]
   (get-in db [::martian (or instance-id ::default-id) :m])))

(re-frame/reg-event-db
 ::on-complete
 (fn [db [_ instance-id req]]
   (update-in db [::martian instance-id :pending] (fnil disj #{}) req)))

(defn- do-request [{:keys [db]} [_ operation-id params on-success on-failure :as request]]
  (let [instance-id (::instance-id params ::default-id)
        params (dissoc params ::instance-id)
        request-key [operation-id params on-success on-failure]
        martian-instance (instance db [:ignore instance-id])]
    (cond
      (not martian-instance)
      {:db (update-in db [::martian instance-id :queue] (fnil conj []) request)}

      (contains? (get-in db [::martian instance-id :pending]) request-key)
      {:db db}

      :else
      {:db (update-in db [::martian instance-id :pending] (fnil conj #{}) request-key)
       ::request [martian-instance instance-id operation-id params on-success on-failure]})))

;; deprecated, use ::request instead
(re-frame/reg-event-fx :http/request do-request)

(re-frame/reg-event-fx ::request do-request)

(re-frame/reg-sub
 ::instance
 instance)

(re-frame/reg-sub
 ::pending-requests
 (fn [db [_ instance-id]]
   (get-in db [::martian (or instance-id ::default-id) :pending])))

(defn init [swagger-url & [params]]
  (go (let [instance-id (::instance-id params)
            martian (<! (martian-http/bootstrap-openapi swagger-url (dissoc params ::instance-id)))]
        (re-frame/dispatch-sync [::init martian instance-id]))))
