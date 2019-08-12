(ns ^:integration rems.application.test-search
  (:require [clojure.test :refer :all]
            [rems.application.search :as search]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-application-search
  ;; generate users with full names and emails
  (test-data/create-users-and-roles!)
  ;; unrelated application - it's an error if any of the tests finds this
  (test-data/create-application! {:actor "developer"})
  (search/refresh!)

  (testing "find by applicant"
    (let [app-id (test-data/create-application! {:actor "alice"})]
      (is (= #{app-id} (search/find-applications "alice")) "user ID, any field")
      (is (= #{app-id} (search/find-applications "applicant:alice")) "user ID")
      (is (= #{app-id} (search/find-applications "applicant:\"Alice Applicant\"")) "name")
      (is (= #{app-id} (search/find-applications "applicant:\"alice@example.com\"")) "email")))


  (testing "find by member"
    (let [app-id (test-data/create-application! {:actor "alice"})]
      (test-data/command! {:type :application.command/submit
                           :application-id app-id
                           :actor "alice"})
      (test-data/command! {:type :application.command/add-member
                           :application-id app-id
                           :actor "developer"
                           :member {:userid "bob"}})
      (is (= #{app-id} (search/find-applications "bob")) "user ID, any field")
      (is (= #{app-id} (search/find-applications "member:bob")) "user ID")
      (is (= #{app-id} (search/find-applications "member:\"Bob Approver\"")) "name")
      (is (= #{app-id} (search/find-applications "member:\"bob@example.com\"")) "email")))

  (testing "find by title"
    (let [form-id (test-data/create-form! {:form/fields [{:field/type :description
                                                          :field/title {:en "Title"}
                                                          :field/optional false}]})
          cat-id (test-data/create-catalogue-item! {:form-id form-id})
          app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                                 :actor "alice"})]
      (test-data/command! {:type :application.command/save-draft
                           :application-id app-id
                           :actor "alice"
                           :field-values [{:field 1
                                           :value "Supercalifragilisticexpialidocious"}]})
      (test-data/command! {:type :application.command/submit ; make sure that the required field was filled in
                           :application-id app-id
                           :actor "alice"})
      (is (= #{app-id} (search/find-applications "Supercalifragilisticexpialidocious")) "any field")
      (is (= #{app-id} (search/find-applications "title:Supercalifragilisticexpialidocious")) "title field")))

  (testing "find by resource"
    (let [cat-id (test-data/create-catalogue-item! {:title {:en "Spam"
                                                            :fi "Nötkötti"}})
          app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                                 :actor "alice"})]
      (is (= #{app-id} (search/find-applications "Spam")) "en title, any field")
      (is (= #{app-id} (search/find-applications "resource:Spam")) "en title")
      (is (= #{app-id} (search/find-applications "resource:Nötkötti")) "fi title")))

  (testing "find by state")

  (testing "find by form content")

  (testing "invalid query"
    (is (= nil (search/find-applications "+")))))
