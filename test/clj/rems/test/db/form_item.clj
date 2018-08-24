(ns rems.test.db.form-item
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.form-item :refer :all]
            [rems.db.core :as db]))

; TODO: deduplicate with test-get-forms
(deftest test-get-form-items
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))]
    (with-redefs [db/get-all-form-items (constantly [{:id :always :start nil :endt nil}
                                                     {:id :expired :start nil :endt yesterday}])]
      (testing "find all forms"
        (is (= #{:always :expired} (set (map :id (get-form-items {})))) "should return also expired"))

      (testing "find active forms"
        (is (= #{:always} (set (map :id (get-form-items {:active? true})))) "should have only active"))

      (testing "calculates :active? property"
        (is (every? #(contains? % :active?) (get-form-items {}))) "should have :active?"))))
