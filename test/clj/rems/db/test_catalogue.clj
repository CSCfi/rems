(ns ^:integration rems.db.test-catalogue
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.common.util :refer [apply-filters]]
            [rems.db.catalogue]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-now-active?
  (let [t0 (time/epoch)
        t1 (time/plus t0 (time/millis 1))
        t2 (time/plus t0 (time/millis 2))
        t3 (time/plus t0 (time/millis 3))
        t4 (time/plus t0 (time/millis 4))
        t5 (time/plus t0 (time/millis 5))]
    (testing "no start & no end"
      (is (rems.db.catalogue/now-active? t1 nil nil) "always active"))
    (testing "start defined"
      (let [start t2]
        (is (not (rems.db.catalogue/now-active? t1 start nil)) "before start")
        (is (rems.db.catalogue/now-active? t2 start nil) "at start")
        (is (rems.db.catalogue/now-active? t3 start nil) "after start")))
    (testing "end defined"
      (let [end t2]
        (is (rems.db.catalogue/now-active? t1 nil end) "before end")
        (is (not (rems.db.catalogue/now-active? t2 nil end)) "at end")
        (is (not (rems.db.catalogue/now-active? t3 nil end)) "after end")))
    (testing "start & end defined"
      (let [start t2
            end t4]
        (is (not (rems.db.catalogue/now-active? t1 start end)) "before start")
        (is (rems.db.catalogue/now-active? t2 start end) "at start")
        (is (rems.db.catalogue/now-active? t3 start end) "between")
        (is (not (rems.db.catalogue/now-active? t4 start end)) "at end")
        (is (not (rems.db.catalogue/now-active? t5 start end)) "after end")))))

(deftest test-assoc-expired
  (is (= {:expired false} (rems.db.catalogue/assoc-expired nil)))
  (is (= {:expired false :start nil :end nil :foobar 42} (rems.db.catalogue/assoc-expired {:start nil :end nil :foobar 42})))
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        tomorrow (time/plus today (time/days 1))]
    (is (= {:expired true :start tomorrow :end nil} (rems.db.catalogue/assoc-expired {:start tomorrow :end nil})))
    (is (= {:expired true :start nil :end yesterday} (rems.db.catalogue/assoc-expired {:start nil :end yesterday})))
    (is (= {:expired false :start yesterday :end tomorrow} (rems.db.catalogue/assoc-expired {:start yesterday :end tomorrow})))
    (is (= {:expired false :start yesterday :end nil} (rems.db.catalogue/assoc-expired {:start yesterday :end nil})))
    (is (= {:expired false :start nil :end tomorrow} (rems.db.catalogue/assoc-expired {:start nil :end tomorrow})))))

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
                         (map rems.db.catalogue/assoc-expired)
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

(deftest test-catalogue-cache
  (testing "cache reload works"
    (let [org-id (test-helpers/create-organization! {})
          form-id (test-helpers/create-form! {:form/external-title {:en "form" :fi "form" :sv "form"}
                                              :form/internal-name "test-form"})
          workflow-id (test-helpers/create-workflow! {:title "workflow"})
          resource-id (test-helpers/create-resource! {:resource-ext-id "test-resource"})
          fixed-time (time/date-time 2020 1 1 12 0)
          item-id (test-helpers/create-catalogue-item! {:resource-id resource-id
                                                        :form-id form-id
                                                        :workflow-id workflow-id
                                                        :organization {:organization/id org-id}
                                                        :title {:en "Test Item"
                                                                :fi "Testiasia"}
                                                        :infourl {:en "http://test.com"
                                                                  :fi "http://test.fi"}
                                                        :start fixed-time})]

      ;; force cache reload
      (cache/set-uninitialized! rems.db.catalogue/catalogue-item-cache)
      (cache/set-uninitialized! rems.db.catalogue/catalogue-item-localizations-cache)

      (is (= {item-id {:archived false
                       :enabled true
                       :end nil
                       :formid form-id
                       :id item-id
                       :localizations {} ; joined from cache. see rems.db.catalogue/localize-catalogue-item
                       :organization {:organization/id org-id}
                       :resource-id resource-id
                       :start fixed-time
                       :wfid workflow-id}}
             (into {} (cache/entries! rems.db.catalogue/catalogue-item-cache))))

      (testing "localizations cache"
        (is (= {item-id {:en {:id item-id
                              :infourl "http://test.com"
                              :langcode :en
                              :title "Test Item"}
                         :fi {:id item-id
                              :infourl "http://test.fi"
                              :langcode :fi
                              :title "Testiasia"}}}
               (into {} (cache/entries! rems.db.catalogue/catalogue-item-localizations-cache))))))))
