(ns rems.administration.test-create-workflow
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.administration.create-workflow :refer [build-create-request build-edit-request]]
            [rems.testing :refer [init-spa-fixture]]))

(use-fixtures :each init-spa-fixture)

(deftest build-create-request-test
  (testing "all workflows"
    (let [form {:organization {:organization/id "abc"}
                :title "workflow title"
                :type :workflow/default
                :forms [{:form/id 123}]
                :handlers ["bob"]
                :licenses [{:id 1}]
                :disable-commands [{:command :application.command/close}]
                :anonymize-handling true}]
      (is (not (nil? (build-create-request form))))
      (is (not (nil? (build-create-request (dissoc form :forms :licenses)))))
      (is (= {:anonymize-handling true
              :licenses [{:license/id 1}]}
             (select-keys (build-create-request form) [:anonymize-handling :licenses])))
      (testing "missing organization"
        (is (nil? (build-create-request (assoc form :organization nil)))))
      (testing "missing title"
        (is (nil? (build-create-request (assoc form :title "")))))
      (testing "missing workflow type"
        (is (nil? (build-create-request (assoc form :type nil)))))
      (testing "invalid workflow type"
        (is (nil? (build-create-request (assoc form :type :no-such-type)))))
      (testing "missing command in disable commands"
        (is (nil? (build-create-request (update form :disable-commands conj {})))))))

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
                :licenses []
                :disable-commands []
                :voting nil
                :anonymize-handling false}
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
                :licenses []
                :disable-commands []
                :voting nil
                :anonymize-handling false}
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
                :licenses []
                :disable-commands []
                :voting nil
                :anonymize-handling false}
               (build-create-request form))))
      (testing "missing handlers"
        (is (nil? (build-create-request (assoc-in form [:handlers] []))))))))

(deftest build-edit-request-test
  (testing "form defaults"
    (is (= {:id 3
            :organization {:organization/id "o"}
            :title "t"
            :handlers ["a" "b"]
            :disable-commands []
            :voting nil
            :anonymize-handling false}
           (build-edit-request 3 {:organization {:organization/id "o"}
                                  :title "t"
                                  :handlers [{:userid "a"} {:userid "b"}]}))))
  (testing "extra options"
    (is (= {:id 3
            :organization {:organization/id "o"}
            :title "t"
            :handlers ["a" "b"]
            :disable-commands [{:command :application.command/close}]
            :voting :handlers-vote
            :anonymize-handling true}
           (build-edit-request 3 {:organization {:organization/id "o"}
                                  :title "t"
                                  :handlers [{:userid "a"} {:userid "b"}]
                                  :licenses [{:id 1}] ; licenses should not be mapped
                                  :disable-commands [{:command :application.command/close}]
                                  :voting :handlers-vote
                                  :anonymize-handling true})))

    (is (= {:id 3
            :organization {:organization/id "o"}
            :title "t"
            :handlers ["a" "b"]
            :disable-commands [{:command :application.command/close}]
            :voting :reviewers-vote
            :anonymize-handling true}
           (build-edit-request 3 {:organization {:organization/id "o"}
                                  :title "t"
                                  :handlers [{:userid "a"} {:userid "b"}]
                                  :licenses [{:id 1}] ; licenses should not be mapped
                                  :disable-commands [{:command :application.command/close}]
                                  :voting :reviewers-vote
                                  :anonymize-handling true}))))

  (is (nil? (build-edit-request nil {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:title "t" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:organization {:organization/id "o"} :title "" :handlers [{:userid "a"} {:userid "b"}]})))
  (is (nil? (build-edit-request 3 {:organization {:organization/id "o"} :title "t" :handlers []})))
  (is (nil? (build-edit-request 3 {:organization {:organization/id "o"} :title "t" :handlers [{:userid "a"} {:userid "b"}] :disable-commands [{}]}))))
