(ns rems.administration.test-create-workflow
  (:require [clojure.test :refer [deftest is testing]]
            [rems.administration.create-workflow :refer [build-create-request build-edit-request]]))

(deftest build-create-request-test
  (testing "all workflows"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :forms [{:form/id 123}]
                :handlers ["bob"]
                :licenses [{:id 1}]}]
      (is (not (nil? (build-create-request form))))
      (is (not (nil? (build-create-request (dissoc form :forms :licenses)))))
      (is (= {:licenses [{:license/id 1}]}
             (select-keys (build-create-request form) [:licenses])))
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
                :forms [{:form/id 13 :form/internal-name "form title"}]
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :forms [{:form/id 13}]
                :handlers ["bob" "carl"]
                :licenses []}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] [])))))))

  (testing "decider workflow"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/decider
                :forms [{:form/id 13 :form/internal-name "form title"}]
                :handlers [{:userid "bob"} {:userid "carl"}]}]
      (testing "valid form"
        (is (= {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/decider
                :forms [{:form/id 13}]
                :handlers ["bob" "carl"]
                :licenses []}
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
                :forms []
                :handlers ["bob" "carl"]
                :licenses []}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] []))))))))

(deftest build-edit-request-test
  (is (= {:id 3
          :organization {:organization/id "o"}
          :title "t"
          :handlers ["a" "b"]}
         (build-edit-request 3 {:organization {:organization/id "o"}
                                :title "t"
                                :handlers [{:userid "a"} {:userid "b"}]
                                :licenses [{:id 1}]}))) ; licenses should not be mapped
  (is (nil? (build-edit-request nil {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:organization {:organization/id "o"} :title "" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:organization {:organization/id "o"} :title "t" :handlers []}))))
