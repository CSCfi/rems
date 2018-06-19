(ns rems.test.db.core
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.core :refer :all]))

(deftest test-contains-all-kv-pairs?
  (is (contains-all-kv-pairs? nil nil))
  (is (contains-all-kv-pairs? {} {}))
  (is (contains-all-kv-pairs? {:a 1 :b 2} {:a 1}))
  (is (contains-all-kv-pairs? {:a 1 :b 2} {:a 1 :b 2}))
  (is (not (contains-all-kv-pairs? {:a 1 :b 2} {:a 2})))
  (is (not (contains-all-kv-pairs? {:a 1 :b 2} {:c 3}))))

(deftest test-now-active?
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        tomorrow (time/plus today (time/days 1))]
    (is (not (now-active? tomorrow nil)) "not yet active")
    (is (now-active? yesterday nil) "already active")
    (is (now-active? nil nil) "always active")
    (is (now-active? nil tomorrow) "still active")
    (is (not (now-active? nil yesterday)) "already expired")))

(deftest test-assoc-active
  (is (= {:active? true} (assoc-active nil)))
  (is (= {:active? true :start nil :endt nil :foobar 42} (assoc-active {:start nil :endt nil :foobar 42})))
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        tomorrow (time/plus today (time/days 1))]
    (is (= {:active? false :start tomorrow :endt nil} (assoc-active {:start tomorrow :endt nil})))
    (is (= {:active? false :start nil :endt yesterday} (assoc-active {:start nil :endt yesterday})))))
