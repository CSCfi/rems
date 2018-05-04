(ns ^:integration rems.test.api.workflow
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest workflow-api-test
  (let [data (-> (request :get "/api/workflow")
                 (authenticate "42" "developer")
                 app
                 read-body)
        wfs (index-by [:title] data)
        simple (get wfs "simple")]
    (is simple)
    (is (= 0 (:fnlround simple)))
    (is (= [{:actoruserid "developer"
             :round 0
             :role "approver"}
            {:actoruserid "bob"
             :round 0
             :role "approver"}]
           (:actors simple)))))

(deftest workflow-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/workflow"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))
