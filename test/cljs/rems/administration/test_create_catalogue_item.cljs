(ns rems.administration.test-catalogue-item
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-catalogue-item :refer [build-request]]))

(deftest build-request-test
  (let [form {:title "the title"
              :workflow {:id 123
                         :unrelated "stuff"}
              :resource {:id 456
                         :unrelated "stuff"}
              :form {:id 789
                     :unrelated "stuff"}}]

    (testing "valid form"
      (is (= {:title "the title"
              :wfid 123
              :resid 456
              :form 789}
             (build-request form))))

    (testing "missing title"
      (is (nil? (build-request (assoc form :title "")))))
    (testing "missing workflow"
      (is (nil? (build-request (assoc form :workflow nil)))))
    (testing "missing resource"
      (is (nil? (build-request (assoc form :resource nil)))))
    (testing "missing form"
      (is (nil? (build-request (assoc form :form nil)))))))
