(ns martian.cljs-http-test
  (:require [martian.cljs-http :as martian-http]
            [martian.protocols :refer [response-for]]
            [cljs.test :refer-macros [deftest testing is run-tests async]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest http-test
  (async done
         (go (let [m (<! (martian-http/bootstrap-swagger "http://localhost:8888/swagger.json"))]

               (let [response (<! (response-for m :create-pet {:name "Doggy McDogFace"
                                                               :type "Dog"
                                                               :age 3}))]
                 (is (= {:status 201
                         :body {:id 123}}
                        (select-keys response [:status :body]))))

               (let [response (<! (response-for m :get-pet {:id 123}))]
                 (is (= {:name "Doggy McDogFace"
                         :type "Dog"
                         :age 3}
                        (:body response)))))
             (done))))
