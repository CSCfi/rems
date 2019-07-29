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
  (let [now (time/now)
        before (time/minus now (time/millis 1))
        after (time/plus now (time/millis 1))]
    (testing "start defined"
      (is (not (now-active? now after nil)) "not yet active")
      (is (now-active? now now nil) "just now activated")
      (is (now-active? now before nil) "already active"))
    (testing "no start & no end"
      (is (now-active? now nil nil) "always active"))
    (testing "end defined"
      (is (now-active? now nil after) "still active")
      (is (not (now-active? now nil now)) "just now expired")
      (is (not (now-active? now nil before)) "already expired"))))

(deftest test-assoc-expired
  (is (= {:expired false} (assoc-expired nil)))
  (is (= {:expired false :start nil :end nil :foobar 42} (assoc-expired {:start nil :end nil :foobar 42})))
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        tomorrow (time/plus today (time/days 1))]
    (is (= {:expired true :start tomorrow :end nil} (assoc-expired {:start tomorrow :end nil})))
    (is (= {:expired true :start nil :end yesterday} (assoc-expired {:start nil :end yesterday})))
    (is (= {:expired false :start yesterday :end tomorrow} (assoc-expired {:start yesterday :end tomorrow})))
    (is (= {:expired false :start yesterday :end nil} (assoc-expired {:start yesterday :end nil})))
    (is (= {:expired false :start nil :end tomorrow} (assoc-expired {:start nil :end tomorrow})))))

(defn- take-ids [items]
  (map :id items))

(deftest test-filtering-active-items
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        all-items [{:id :normal
                    :start nil
                    :end nil}
                   {:id :expired
                    :start nil
                    :end yesterday}]

        ; the following idiom can be used when reading database entries with 'end' field
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
