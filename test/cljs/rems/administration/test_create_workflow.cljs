(ns rems.administration.test-create-workflow
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.administration.create-workflow :refer [build-create-request build-edit-request]]
            [rems.common.application-util :as application-util]
            [rems.testing :refer [init-spa-fixture]]))

(use-fixtures :each init-spa-fixture)

(def form-all-fields
  {:anonymize-handling false
   :disable-commands [{:command :application.command/close :when/state [:application.state/returned] :when/role [:applicant]}]
   :forms [{:form/id 123}]
   :handlers [{:userid "bob"}]
   :licenses [{:id 1}]
   :organization {:organization/id "organization1"}
   :processing-states [{:title {:en "In voting"} :value "in voting"}]
   :title "workflow form all fields"
   :type :workflow/default
   :voting :handlers-vote})

(deftest build-create-request-test
  (doseq [[testing-context workflow] [["default workflow" (:workflow/default application-util/workflow-types)]
                                      ["decider workflow" (:workflow/decider application-util/workflow-types)]
                                      ["master workflow" (:workflow/master application-util/workflow-types)]]
          :let [workflow-form-all-fields (assoc form-all-fields :type workflow)]]

    (testing testing-context
      (testing "all form fields"
        (is (= {:anonymize-handling false
                :disable-commands [{:command :application.command/close :when/state [:application.state/returned] :when/role [:applicant]}]
                :forms [{:form/id 123}]
                :handlers ["bob"]
                :licenses [{:license/id 1}]
                :organization {:organization/id "organization1"}
                :processing-states [{:title {:en "In voting"} :value "in voting"}]
                :title "workflow form all fields"
                :type workflow
                :voting :handlers-vote}
               (build-create-request workflow-form-all-fields))))

      (testing "required fields"
        (is (= {:anonymize-handling nil
                :disable-commands []
                :forms []
                :handlers ["bob"]
                :licenses []
                :organization {:organization/id "organization1"}
                :processing-states nil
                :title "workflow form all fields"
                :type workflow
                :voting nil}
               (build-create-request (select-keys workflow-form-all-fields [:handlers :organization :title :type])))))

      (testing "invalid fields"
        (is (= nil
               (build-create-request (dissoc workflow-form-all-fields :organization))
               (build-create-request (assoc workflow-form-all-fields :organization "abc")))
            "missing or invalid organization")
        (is (= nil
               (build-create-request (dissoc workflow-form-all-fields :title))
               (build-create-request (assoc workflow-form-all-fields :title "")))
            "missing or empty title")
        (is (= nil
               (build-create-request (dissoc workflow-form-all-fields :type))
               (build-create-request (assoc workflow-form-all-fields :type :no-such-type)))
            "missing or invalid workflow type")
        (is (= nil
               (build-create-request (dissoc workflow-form-all-fields :handlers))
               (build-create-request (assoc workflow-form-all-fields :handlers [{}])))
            "missing or invalid handlers")
        (is (= nil
               (build-create-request (assoc workflow-form-all-fields :disable-commands [{:command :application.command/close}
                                                                                        {:when/state [:application.state/returned]}
                                                                                        {:when/role [:applicant]}])))
            "invalid disable commands")
        (is (= nil
               (build-create-request (assoc workflow-form-all-fields :processing-states [{:title "i should be localized" :value "in error"}])))
            "invalid processing states")))))

(deftest build-edit-request-test
  (testing "all form fields"
    (is (= {:anonymize-handling false
            :disable-commands [{:command :application.command/close :when/state [:application.state/returned] :when/role [:applicant]}]
            :handlers ["bob"]
            :id 3
            :organization {:organization/id "organization1"}
            :processing-states [{:title {:en "In voting"} :value "in voting"}]
            :title "workflow form all fields"
            :voting :handlers-vote}
           (build-edit-request 3 form-all-fields))))

  (testing "required fields"
    (is (= {:anonymize-handling nil
            :disable-commands []
            :handlers ["bob"]
            :id 3
            :organization {:organization/id "organization1"}
            :processing-states nil
            :title "workflow form all fields"
            :voting nil}
           (build-edit-request 3 (select-keys form-all-fields [:handlers :organization :title])))))

  (testing "invalid fields"
    (is (= nil
           (build-edit-request 3 (dissoc form-all-fields :organization))
           (build-edit-request 3 (assoc form-all-fields :organization "abc")))
        "missing or invalid organization")
    (is (= nil
           (build-edit-request 3 (dissoc form-all-fields :title))
           (build-edit-request 3 (assoc form-all-fields :title "")))
        "missing or empty title")
    (is (= nil
           (build-edit-request 3 (dissoc form-all-fields :handlers))
           (build-edit-request 3 (assoc form-all-fields :handlers [{}])))
        "missing or invalid handlers")
    (is (= nil
           (build-edit-request 3 (assoc form-all-fields :disable-commands [{:command :application.command/close}
                                                                           {:when/state [:application.state/returned]}
                                                                           {:when/role [:applicant]}])))
        "invalid disable commands")
    (is (= nil
           (build-edit-request 3 (assoc form-all-fields :processing-states [{:title "i should be localized" :value "in error"}])))
        "invalid processing states")))
