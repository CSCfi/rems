(ns rems.administration.test-create-workflow
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-workflow :refer [build-create-request build-edit-request]]))

(deftest build-create-request-test
  (testing "all workflows"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :handlers ["bob"]}]
      (is (not (nil? (build-create-request form))))
      (testing "missing organization"
        (is (nil? (build-create-request (assoc form :organization nil)))))
      (testing "missing title"
        (is (nil? (build-create-request (assoc form :title "")))))
      (testing "missing workflow type"
        (is (nil? (build-create-request (assoc form :type nil)))))
      (testing "invalid workflow type"
        (is (nil? (build-create-request (assoc form :type :no-such-type)))))))

  (testing "default workflow"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :handlers ["bob" "carl"]}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] [])))))))

  (testing "decider workflow"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/decider
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/decider
                :handlers ["bob" "carl"]}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] [])))))))

  (testing "master workflow"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/master
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/master
                :handlers ["bob" "carl"]}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] []))))))))

(deftest build-edit-request-test
  (is (= {:id 3 :title "t" :handlers ["a" "b"]}
         (build-edit-request 3 {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request nil {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:title "" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:title "t" :handlers []}))))
