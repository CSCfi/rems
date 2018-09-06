(ns rems.test.administration.resource
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.resource :refer [build-request]]))

(deftest build-request-test
  (let [form {:prefix "abc"
              :resid "resource id"
              :licenses #{{:id 123
                           :unrelated "stuff"}}}]
    (testing "valid form"
      (is (= {:prefix "abc"
              :resid "resource id"
              :licenses [123]}
             (build-request form))))

    (testing "selecting a license is optional"
      (is (= {:prefix "abc"
              :resid "resource id"
              :licenses []}
             (build-request (assoc form :licenses #{})))))

    (testing "missing prefix"
      (is (nil? (build-request (assoc form :prefix "")))))
    (testing "missing resource id"
      (is (nil? (build-request (assoc form :resid "")))))))
