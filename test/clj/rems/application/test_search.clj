(ns ^:integration rems.application.test-search
  (:require [clojure.test :refer :all]
            [rems.application.search :as search]
            [rems.db.applications :as applications]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture search-index-fixture]]))

(use-fixtures :once (join-fixtures [test-db-fixture
                                    search-index-fixture]))

(deftest test-application-search
  ;; generate users with full names and emails
  (test-data/create-users-and-roles!)
  ;; unrelated application - it's an error if any of the tests finds this
  (test-data/create-application! {:actor "developer"})

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
                           :member {:userid "elsa"}})
      (is (= #{app-id} (search/find-applications "elsa")) "user ID, any field")
      (is (= #{app-id} (search/find-applications "member:elsa")) "user ID")
      (is (= #{app-id} (search/find-applications "member:\"Elsa Roleless\"")) "name")
      (is (= #{app-id} (search/find-applications "member:\"elsa@example.com\"")) "email")))

  (testing "find by ID"
    (let [app-id (test-data/create-application! {:actor "alice"})
          app (applications/get-unrestricted-application app-id)]
      (is (= #{app-id} (search/find-applications (str app-id))) "app ID, any field")
      (is (= #{app-id} (search/find-applications (str "id:" app-id))) "app ID")
      (is (= #{app-id} (search/find-applications (str "id:\"" (:application/external-id app) "\""))) "external ID")))

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

  (testing "find by state"
    (let [app-id (test-data/create-application! {:actor "alice"})]
      (test-data/command! {:type :application.command/submit
                           :application-id app-id
                           :actor "alice"})
      (test-data/command! {:type :application.command/approve
                           :application-id app-id
                           :actor "developer"
                           :comment ""})
      (is (= #{app-id} (search/find-applications "Approved")) "en status, any field")
      (is (= #{app-id} (search/find-applications "state:Approved")) "en status")
      (is (= #{app-id} (search/find-applications "state:Hyväksytty")) "fi status")))

  (testing "find by todo"
    (let [app-id (test-data/create-application! {:actor "alice"})]
      (test-data/command! {:type :application.command/submit
                           :application-id app-id
                           :actor "alice"})
      (test-data/command! {:type :application.command/request-comment
                           :application-id app-id
                           :actor "developer"
                           :commenters ["elsa"]
                           :comment ""})
      (is (= #{app-id} (search/find-applications "\"Waiting for a review\"")) "en todo, any field")
      (is (= #{app-id} (search/find-applications "\"waiting-for-review\"")) "keyword todo, any field")
      (is (= #{app-id} (search/find-applications "todo:\"Waiting for a review\"")) "en todo")
      (is (= #{app-id} (search/find-applications "todo:\"Odottaa katselmointia\"")) "fi todo")
      (is (= #{app-id} (search/find-applications "todo:\"waiting-for-review\"")) "keyword todo, any field")))

  (testing "find by form content"
    (let [form-id (test-data/create-form! {:form/fields [{:field/type :text
                                                          :field/title {:en "Text field"}
                                                          :field/optional false}]})
          cat-id (test-data/create-catalogue-item! {:form-id form-id})
          app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                                 :actor "alice"})]
      (test-data/command! {:type :application.command/save-draft
                           :application-id app-id
                           :actor "alice"
                           :field-values [{:field 1
                                           :value "Tis but a scratch."}]})
      (is (= #{app-id} (search/find-applications "scratch")) "any field")
      (is (= #{app-id} (search/find-applications "form:scratch")) "form field")))

  (testing "updating applications"
    (let [form-id (test-data/create-form! {:form/fields [{:field/type :text
                                                          :field/title {:en "Text field"}
                                                          :field/optional false}]})
          cat-id (test-data/create-catalogue-item! {:form-id form-id})
          app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                                 :actor "alice"})]
      (test-data/command! {:type :application.command/save-draft
                           :application-id app-id
                           :actor "alice"
                           :field-values [{:field 1
                                           :value "version1"}]})
      (is (= #{app-id} (search/find-applications "version1"))
          "original version is indexed")

      (test-data/command! {:type :application.command/save-draft
                           :application-id app-id
                           :actor "alice"
                           :field-values [{:field 1
                                           :value "version2"}]})
      (is (= #{} (search/find-applications "version1"))
          "should not find old versions")
      (is (= #{app-id} (search/find-applications "version2"))
          "should find the new version")))

  (testing "multiple results"
    (is (< 1 (count (search/find-applications "alice")))))

  (testing "invalid query"
    (is (= nil (search/find-applications "+")))))
