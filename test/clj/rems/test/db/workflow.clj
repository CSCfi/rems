(ns rems.test.db.workflow

  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.workflow :refer :all]
            [rems.db.core :as db]))

(deftest test-contains-all-kv-pairs?
  (is (contains-all-kv-pairs? nil nil))
  (is (contains-all-kv-pairs? {} {}))
  (is (contains-all-kv-pairs? {:a 1 :b 2} {:a 1}))
  (is (contains-all-kv-pairs? {:a 1 :b 2} {:a 1 :b 2}))
  (is (not (contains-all-kv-pairs? {:a 1 :b 2} {:a 2})))
  (is (not (contains-all-kv-pairs? {:a 1 :b 2} {:c 3}))))

(deftest test-get-all-workflows
  (testing "find all workflows"
    (with-redefs [db/get-workflows (constantly [{:id :always :start nil :endt nil}])]
      (is (= #{:always} (set (map :id (get-workflows)))))

      (testing "calculates active? property"
        (is (:active? (first (get-workflows))) ":always should be active"))))

  (testing "find only active workflows"
    (let [today (time/now)
          yesterday (time/minus today (time/days 1))
          tomorrow (time/plus today (time/days 1))]
      (with-redefs [db/get-workflows (constantly [{:id :not-yet-active :start tomorrow :endt nil}
                                                  {:id :already-active :start yesterday :endt nil}
                                                  {:id :always-active :start nil :endt nil}
                                                  {:id :still-active :start nil :endt tomorrow}
                                                  {:id :expired :start nil :endt yesterday}])]
        (is (= #{:always-active
                 :still-active
                 :already-active}
               (set (map :id (get-workflows {:active? true})))))))))
