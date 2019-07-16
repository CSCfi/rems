(ns rems.administration.test-create-catalogue-item
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-catalogue-item :refer [build-request]]))

(deftest build-request-test
  (let [form {:title {:en "en title"
                      :fi "fi title"}
              :workflow {:id 123
                         :unrelated "stuff"}
              :resource {:id 456
                         :unrelated "stuff"}
              :form {:form/id 789
                     :unrelated "stuff"}}
        languages [:en :fi]]

    (testing "valid form"
      (is (= {:title "en title"
              :wfid 123
              :resid 456
              :form 789
              :localizations [{:langcode "en"
                               :title "en title"}
                              {:langcode "fi"
                               :title "fi title"}]}
             (build-request form languages))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title :en] "")
                               languages)))
      (is (nil? (build-request (assoc-in form [:title :fi] "")
                               languages))))
    (testing "missing workflow"
      (is (nil? (build-request (assoc form :workflow nil)
                               languages))))
    (testing "missing resource"
      (is (nil? (build-request (assoc form :resource nil)
                               languages))))
    (testing "missing form"
      (is (nil? (build-request (assoc form :form nil)
                               languages))))))
