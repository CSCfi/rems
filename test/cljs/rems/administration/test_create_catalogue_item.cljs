(ns rems.administration.test-create-catalogue-item
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [reagent.core :as r]
            [rems.administration.create-catalogue-item :refer [build-request filter-possible-child-items]]
            [rems.globals]
            [rems.testing :refer [init-spa-fixture set-roles!]]
            [rems.common.roles :as roles]))

(defn- languages-fixture [langs]
  (fn [f]
    (r/rswap! rems.globals/config assoc :languages langs)
    (f)))

(use-fixtures
  :each
  init-spa-fixture
  (languages-fixture [:en :fi]))

(deftest build-request-test
  (let [form {:title {:en "en title"
                      :fi "fi title"}
              :infourl {:en "hello"
                        :fi ""}
              :organization {:organization/id "organization1"}
              :workflow {:id 123
                         :organization {:organization/id "organization1"}}
              :resource {:id 456
                         :organization {:organization/id "organization1"}}
              :form {:form/id 789
                     :organization {:organization/id "organization1"}}
              :categories [{:category/id 1
                            :category/title {:fi "fi" :en "en" :sv "sv"}
                            :category/description {:fi "fi" :en "en" :sv "sv"}
                            :category/children []}]}]

    (testing "valid form"
      (is (= {:wfid 123
              :resid 456
              :form 789
              :organization {:organization/id "organization1"}
              :localizations {:en {:title "en title"
                                   :infourl "hello"}
                              :fi {:title "fi title"
                                   :infourl nil}}
              :categories [{:category/id 1}]}
             (build-request form))))

    (testing "missing form"
      (is (= {:wfid 123
              :resid 456
              :form nil
              :organization {:organization/id "organization1"}
              :localizations {:en {:title "en title"
                                   :infourl "hello"}
                              :fi {:title "fi title"
                                   :infourl nil}}
              :categories [{:category/id 1}]}
             (build-request (assoc form :form nil)))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title :en] ""))))
      (is (nil? (build-request (assoc-in form [:title :fi] "")))))

    (testing "missing organization"
      (is (nil? (build-request (dissoc form :organization)))))

    (testing "missing workflow"
      (is (nil? (build-request (assoc form :workflow nil)))))
    (testing "missing resource"
      (is (nil? (build-request (assoc form :resource nil)))))))

(deftest test-filter-possible-child-items
  (let [id-1-archived {:id 1 :archived true}
        id-2 {:id 2 :wfid 1}
        id-3-parent {:id 3 :wfid 1 :children [{:catalogue-item/id 4}]}
        id-4-child {:id 4 :wfid 1 :part-of {:catalogue-item/id 3}}
        id-5 {:id 5 :wfid 1}
        id-6-diff-wfid {:id 6 :wfid 2}
        id-7-parent {:id 7 :wfid 1 :children [{:catalogue-item/id 8}]}
        id-8-child {:id 8 :wfid 1 :part-of {:catalogue-item/id 7}}
        catalogue [id-1-archived id-2 id-3-parent id-4-child id-5 id-6-diff-wfid id-7-parent id-8-child]]

    (set-roles! [:owner])

    (testing "with archived items"
      (is (= []
             (filter-possible-child-items nil [id-1-archived])
             (filter-possible-child-items id-2 [id-1-archived id-2]))
          "doesn't return archived items"))

    (testing "with catalogue items that have children or a parent"
      (is (= [id-2]
             (filter-possible-child-items nil [id-2 id-3-parent])
             (filter-possible-child-items nil [id-2 id-4-child])
             (filter-possible-child-items nil [id-2 id-3-parent id-4-child])))
      (is (= []
             (filter-possible-child-items id-2 [id-2 id-3-parent id-4-child id-7-parent id-8-child]))))

    (testing "with different workflow id"
      (is (= [id-2 id-6-diff-wfid]
             (filter-possible-child-items nil [id-2 id-6-diff-wfid]))
          "when creating, doesn't filter")
      (is (= [id-2 id-5 id-6-diff-wfid]
             (filter-possible-child-items nil catalogue)))
      (is (= [id-5]
             (filter-possible-child-items id-2 [id-5 id-6-diff-wfid])
             (filter-possible-child-items id-2 catalogue))
          "when editing, filters by parent workflow id"))

    (testing "when editing"
      (is (= []
             (filter-possible-child-items id-2 [id-2]))
          "doesn't suggest itself (circular dependency)")
      (is (= [id-4-child]
             (filter-possible-child-items id-3-parent [id-4-child])
             (filter-possible-child-items id-3-parent [id-4-child id-7-parent id-8-child]))
          "parent item can see it's child item"))

    (testing "with different organization"
      (set-roles! [:not-the-owner])
      (is (= []
             (filter-possible-child-items id-2 catalogue))
          "with no organizations in test fixtures, `roles/can-modify-organization-item?` returns false to the same effect than not having write access to the organization"))))
