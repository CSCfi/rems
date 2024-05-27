(ns rems.administration.test-create-catalogue-item
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [reagent.core :as r]
            [rems.administration.create-catalogue-item :refer [build-request]]
            [rems.globals]
            [rems.testing :refer [init-spa-fixture]]))

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
