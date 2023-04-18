(ns rems.db.test-catalogue
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.common.util :refer [apply-filters]]
            [rems.db.catalogue :refer :all]))

(deftest test-now-active?
  (let [t0 (time/epoch)
        t1 (time/plus t0 (time/millis 1))
        t2 (time/plus t0 (time/millis 2))
        t3 (time/plus t0 (time/millis 3))
        t4 (time/plus t0 (time/millis 4))
        t5 (time/plus t0 (time/millis 5))]
    (testing "no start & no end"
      (is (now-active? t1 nil nil) "always active"))
    (testing "start defined"
      (let [start t2]
        (is (not (now-active? t1 start nil)) "before start")
        (is (now-active? t2 start nil) "at start")
        (is (now-active? t3 start nil) "after start")))
    (testing "end defined"
      (let [end t2]
        (is (now-active? t1 nil end) "before end")
        (is (not (now-active? t2 nil end)) "at end")
        (is (not (now-active? t3 nil end)) "after end")))
    (testing "start & end defined"
      (let [start t2
            end t4]
        (is (not (now-active? t1 start end)) "before start")
        (is (now-active? t2 start end) "at start")
        (is (now-active? t3 start end) "between")
        (is (not (now-active? t4 start end)) "at end")
        (is (not (now-active? t5 start end)) "after end")))))

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

        ;; the following idiom can be used when reading database entries with 'end' field
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
