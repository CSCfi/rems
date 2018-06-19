(ns rems.test.db.workflow
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.workflow :refer :all]
            [rems.db.core :as db]))

(deftest test-get-workflows
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))]
    (with-redefs [db/get-workflows (constantly [{:id :always :start nil :endt nil}
                                                {:id :expired :start nil :endt yesterday}])]
      (testing "find all workflows"
        (is (= #{:always :expired} (set (map :id (get-workflows {})))) "should return also expired"))

      (testing "find active workflows"
        (is (= #{:always} (set (map :id (get-workflows {:active? true})))) "should have only active"))

      (testing "calculates :active? property"
        (is (every? #(contains? % :active?) (get-workflows {}))) "should have :active?"))))
