(ns rems.administration.test-create-catalogue-item
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-catalogue-item :refer [build-request]]))

(deftest build-request-test
  (let [form {:title {:en "en title"
                      :fi "fi title"}
              :workflow-id 123
              :resource-id 456
              :form-id 789}
        languages [:en :fi]]

    (testing "valid form"
      (is (= {:wfid 123
              :resid 456
              :form 789
              :localizations {:en {:title "en title"}
                              :fi {:title "fi title"}}}
             (build-request form languages))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title :en] "")
                               languages)))
      (is (nil? (build-request (assoc-in form [:title :fi] "")
                               languages))))
    (testing "missing workflow id"
      (is (nil? (build-request (assoc form :workflow-id nil)
                               languages))))
    (testing "missing resource id"
      (is (nil? (build-request (assoc form :resource-id nil)
                               languages))))
    (testing "missing form id"
      (is (nil? (build-request (assoc form :form-id nil)
                               languages))))))
