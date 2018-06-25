(ns rems.test.db.form
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.form :refer :all]
            [rems.db.core :as db]))

(deftest test-get-forms
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))]
    (with-redefs [db/get-forms (constantly [{:id :always :start nil :endt nil}
                                                {:id :expired :start nil :endt yesterday}])]
      (testing "find all forms"
        (is (= #{:always :expired} (set (map :id (get-forms {})))) "should return also expired"))

      (testing "find active forms"
        (is (= #{:always} (set (map :id (get-forms {:active? true})))) "should have only active"))

      (testing "calculates :active? property"
        (is (every? #(contains? % :active?) (get-forms {}))) "should have :active?"))))
