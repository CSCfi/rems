(ns rems.administration.test-create-workflow
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
        (is (nil? (build-request (assoc-in form [:handlers] []))))))))
