(ns rems.administration.test-create-resource
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-resource :refer [build-request]]))

(deftest build-request-test
  (let [form {:organization {:organization/id "organization1"}
              :resid "resource id"
              :licenses [{:id 123
                          :organization "organization1"
                          :unrelated "stuff"}]}]
    (testing "valid form"
      (is (= {:organization "organization1"
              :resid "resource id"
              :licenses [123]}
             (build-request form))))

    (testing "selecting a license is optional"
      (is (= {:organization "organization1"
              :resid "resource id"
              :licenses []}
             (build-request (assoc form :licenses [])))))

    (testing "missing organization"
      (is (nil? (build-request (assoc form :organization "")))))

    (testing "missing resource id"
      (is (nil? (build-request (assoc form :resid "")))))))
