(ns rems.test.db.resource
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.resource :refer :all]
            [rems.db.core :as db]))

(deftest test-get-resources
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))]
    (with-redefs [db/get-resources (constantly [{:id :always :start nil :endt nil}
                                                {:id :expired :start nil :endt yesterday}])]
      (testing "find all resources"
        (is (= #{:always :expired} (set (map :id (get-resources {})))) "should return also expired"))

      (testing "find active resources"
        (is (= #{:always} (set (map :id (get-resources {:active? true})))) "should have only active"))

      (testing "calculates :active? property"
        (is (every? #(contains? % :active?) (get-resources {}))) "should have :active?"))))
