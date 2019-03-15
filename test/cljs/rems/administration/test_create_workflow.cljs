(ns rems.administration.test-workflow
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-workflow :refer [build-request]]))

(deftest build-request-test
  (testing "all workflows"
    (let [form {:organization "abc"
                :title "workflow title"
                :type :auto-approve}]
      (is (not (nil? (build-request form))))
      (testing "missing organization"
        (is (nil? (build-request (assoc form :organization "")))))
      (testing "missing title"
        (is (nil? (build-request (assoc form :title "")))))
      (testing "missing workflow type"
        (is (thrown-with-msg? js/Error #"No matching clause"
                              (build-request (assoc form :type nil)))))
      (testing "invalid workflow type"
        (is (thrown-with-msg? js/Error #"No matching clause"
                              (build-request (assoc form :type :no-such-type)))))))

  (testing "auto-approved workflow"
    (let [form {:organization "abc"
                :title "workflow title"
                :type :auto-approve}]
      (testing "valid form"
        (is (= {:organization "abc"
                :title "workflow title"
                :type :auto-approve}
               (build-request form))))))

  (testing "dynamic workflow"
    (let [form {:organization "abc"
                :title "workflow title"
                :type :dynamic
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization "abc"
                :title "workflow title"
                :type :dynamic
                :handlers ["bob" "carl"]}
               (build-request form))))
      (testing "missing handlers"
        (is (nil? (build-request (assoc-in form [:handlers] [])))))))

  (testing "static rounds workflow"
    (let [form {:organization "abc"
                :title "workflow title"
                :type :rounds
                :rounds [{:type :review
                          :actors [{:userid "alice"} {:userid "bob"}]}
                         {:type :approval
                          :actors [{:userid "carl"}]}]}]
      (testing "valid form"
        (is (= {:organization "abc"
                :title "workflow title"
                :type :rounds
                :rounds [{:type :review
                          :actors ["alice" "bob"]}
                         {:type :approval
                          :actors ["carl"]}]}
               (build-request form))))
      (testing "missing round type"
        (is (nil? (build-request (assoc-in form [:rounds 0 :type] nil)))))
      (testing "missing actors"
        (is (nil? (build-request (assoc-in form [:rounds 0 :actors] []))))))))
