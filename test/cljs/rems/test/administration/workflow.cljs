(ns rems.test.administration.workflow
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.workflow :refer [build-request vec-dissoc]]))

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
    (testing "auto-approved workflow"
      (is (= {:prefix "abc"
              :title "workflow title"
              :rounds []}
             (build-request (assoc form :rounds [])))))
    (testing "missing prefix"
      (is (nil? (build-request (assoc form :prefix "")))))
    (testing "missing title"
      (is (nil? (build-request (assoc form :title "")))))
    (testing "missing round type"
      (is (nil? (build-request (assoc-in form [:rounds 0 :type] nil)))))
    (testing "missing actors"
      (is (nil? (build-request (assoc-in form [:rounds 0 :actors] [])))))))

(deftest vec-dissoc-test
  (is (vector? (vec-dissoc ["a"] 0)))
  (is (= [] (vec-dissoc ["a"] 0)))
  (is (= ["b", "c"] (vec-dissoc ["a", "b", "c"] 0)))
  (is (= ["a", "c"] (vec-dissoc ["a", "b", "c"] 1)))
  (is (= ["a", "b"] (vec-dissoc ["a", "b", "c"] 2))))
