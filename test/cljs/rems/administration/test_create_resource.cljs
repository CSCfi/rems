(ns rems.administration.test-create-resource
  (:require [clojure.test :refer [deftest is testing]]
            [rems.administration.create-resource :refer [build-request]]))

(deftest build-request-test
  (let [form {:organization {:organization/id "organization1"}
              :resid "resource id"
              :licenses [{:id 123
                          :organization {:organization/id "organization1"}
                          :unrelated "stuff"}]}]
    (testing "valid form"
      (is (= {:organization {:organization/id "organization1"}
              :resid "resource id"
              :licenses [123]
              :resource/duo {:duo/codes []}}
             (build-request form))))

    (testing "selecting a license is optional"
      (is (= {:organization {:organization/id "organization1"}
              :resid "resource id"
              :licenses []
              :resource/duo {:duo/codes []}}
             (build-request (assoc form :licenses [])))))

    (testing "missing organization"
      (is (nil? (build-request (assoc form :organization "")))))

    (testing "missing resource id"
      (is (nil? (build-request (assoc form :resid "")))))))
