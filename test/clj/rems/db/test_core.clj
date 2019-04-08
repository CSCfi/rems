(ns rems.db.test-core
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

(deftest test-assoc-expired
  (is (= {:expired false} (assoc-expired nil)))
  (is (= {:expired false :start nil :endt nil :foobar 42} (assoc-expired {:start nil :endt nil :foobar 42})))
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        tomorrow (time/plus today (time/days 1))]
    (is (= {:expired true :start tomorrow :endt nil} (assoc-expired {:start tomorrow :endt nil})))
    (is (= {:expired true :start nil :endt yesterday} (assoc-expired {:start nil :endt yesterday})))))

(defn- take-ids [items]
  (map :id items))

(deftest test-filtering-active-items
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        all-items [{:id :normal
                    :start nil
                    :endt nil}
                   {:id :expired
                    :start nil
                    :endt yesterday}]

        ; the following idiom can be used when reading database entries with 'endt' field
        get-items (fn [filters]
                    (->> all-items
                         (map assoc-expired)
                         (apply-filters filters)))]

    (testing "find all items"
      (is (= [:normal :expired] (take-ids (get-items {}))))
      (is (= [:normal :expired] (take-ids (get-items nil)))))

    (testing "find active items"
      (is (= [:normal] (take-ids (get-items {:expired false})))))

    (testing "find expired items"
      (is (= [:expired] (take-ids (get-items {:expired true})))))

    (testing "calculates :active property"
      (is (every? #(contains? % :expired) (get-items {}))))))
