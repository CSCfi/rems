(ns ^:integration rems.email.test-handler-reminder
  (:require [clojure.test :refer :all]
            [rems.api.services.workflow :as workflow]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.email.handler-reminder :as handler-reminder]))

(use-fixtures
  :each
  test-db-fixture
  rollback-db-fixture)

(deftest test-get-handlers
  (let [wf1 (:id (workflow/create-workflow! {:user-id "owner"
                                             :organization ""
                                             :type :workflow/default
                                             :title "workflow2"
                                             :handlers ["handler1"
                                                        "handler2"]}))
        wf2 (:id (workflow/create-workflow! {:user-id "owner"
                                             :organization ""
                                             :type :workflow/default
                                             :title "workflow2"
                                             :handlers ["handler2"
                                                        "handler3"]}))]

    (testing "returns distinct handlers from all workflows"
      (is (= ["handler1" "handler2" "handler3"]
             (handler-reminder/get-handlers))))

    (testing "ignores disabled workflows"
      (workflow/set-workflow-enabled! {:id wf1 :enabled false})
      (is (= ["handler2" "handler3"]
             (handler-reminder/get-handlers))))

    (testing "ignores archived workflows"
      (workflow/set-workflow-archived! {:id wf2 :archived true})
      (is (= []
             (handler-reminder/get-handlers))))))
