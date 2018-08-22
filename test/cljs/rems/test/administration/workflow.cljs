(ns rems.test.administration.workflow
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.workflow :refer [build-request]]))

(deftest build-request-test
  (let [form {:prefix "abc"
              :title "workflow title"
              :rounds [{:type :review
                        :actors [{:userid "alice"}
                                 {:userid "bob"}]}
                       {:type :approval
                        :actors [{:userid "carl"}]}]}]
    (testing "valid form"
      (is (= {:prefix "abc"
              :title "workflow title"
              :rounds [{:type :review
                        :actors [{:userid "alice"}
                                 {:userid "bob"}]}
                       {:type :approval
                        :actors [{:userid "carl"}]}]}
             (build-request form))))
    (testing "missing prefix"
      (is (nil? (build-request (assoc form :prefix "")))))
    (testing "missing title"
      (is (nil? (build-request (assoc form :title "")))))
    (testing "no rounds"
      (is (nil? (build-request (assoc form :rounds [])))))
    (testing "missing round type"
      (is (nil? (build-request (assoc-in form [:rounds 0 :type] nil)))))
    (testing "no actors"
      (is (nil? (build-request (assoc-in form [:rounds 0 :actors] [])))))))
