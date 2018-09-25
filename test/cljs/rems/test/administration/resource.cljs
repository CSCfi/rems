(ns rems.test.administration.resource
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.resource :refer [build-request]]))

(deftest build-request-test
  (let [form {:organization "abc"
              :resid "resource id"
              :licenses #{{:id 123
                           :unrelated "stuff"}}}]
    (testing "valid form"
      (is (= {:organization "abc"
              :resid "resource id"
              :licenses [123]}
             (build-request form))))

    (testing "selecting a license is optional"
      (is (= {:organization "abc"
              :resid "resource id"
              :licenses []}
             (build-request (assoc form :licenses #{})))))

    (testing "missing organization"
      (is (nil? (build-request (assoc form :organization "")))))
    (testing "missing resource id"
      (is (nil? (build-request (assoc form :resid "")))))))
