(ns ^:integration rems.application.test-search
  (:require [clojure.test :refer :all]
            [rems.application.search :as search]
            [rems.db.applications :as applications]
            [rems.db.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture search-index-fixture test-db-fixture]]))

(use-fixtures :once
  test-db-fixture
  rollback-db-fixture
  search-index-fixture)

(deftest test-application-search
  ;; generate users with full names and emails
  (test-data/create-test-users-and-roles!)
  ;; unrelated application - it's an error if any of the tests finds this
  (test-helpers/create-application! {:actor "developer"})

  (testing "find by applicant"
    (let [app-id (test-helpers/create-application! {:actor "alice"})]
      (is (= #{app-id} (search/find-applications "alice")) "user ID, any field")
      (is (= #{app-id} (search/find-applications "applicant:alice")) "user ID")
      (is (= #{app-id} (search/find-applications "applicant:\"Alice Applicant\"")) "name")
      (is (= #{app-id} (search/find-applications "applicant:\"alice@example.com\"")) "email")))

  (testing "find by member"
    (let [app-id (test-helpers/create-application! {:actor "alice"})]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/add-member
                              :application-id app-id
                              :actor "developer"
                              :member {:userid "elsa"}})
      (is (= #{app-id} (search/find-applications "elsa")) "user ID, any field")
      (is (= #{app-id} (search/find-applications "member:elsa")) "user ID")
      (is (= #{app-id} (search/find-applications "member:\"Elsa Roleless\"")) "name")
      (is (= #{app-id} (search/find-applications "member:\"elsa@example.com\"")) "email")))

  (testing "find by ID"
    (let [app-id (test-helpers/create-application! {:actor "alice"})
          app (applications/get-application app-id)]
      (is (= #{app-id} (search/find-applications (str app-id))) "app ID, any field")
      (is (= #{app-id} (search/find-applications (str "id:" app-id))) "app ID")
      (is (= #{app-id} (search/find-applications (str "id:\"" (:application/external-id app) "\""))) "external ID")))

  (testing "find by title"
    (let [form-id (test-helpers/create-form! {:form/fields [{:field/id "abc"
                                                             :field/type :description
                                                             :field/title {:en "Title"
                                                                           :fi "Titteli"
                                                                           :sv "Titel"}
                                                             :field/optional false}]})
          cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
          app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                    :actor "alice"})]
      (test-helpers/command! {:type :application.command/save-draft
                              :application-id app-id
                              :actor "alice"
                              :field-values [{:form form-id
                                              :field "abc"
                                              :value "Supercalifragilisticexpialidocious"}]})
      (is (= #{app-id} (search/find-applications "Supercalifragilisticexpialidocious")) "any field")
      (is (= #{app-id} (search/find-applications "title:Supercalifragilisticexpialidocious")) "title field")))

  (testing "find by resource"
    (let [resource (test-helpers/create-resource! {:resource-ext-id "urn:fi:abcd"})
          cat-id (test-helpers/create-catalogue-item! {:resource-id resource
                                                       :title {:en "Spam"
                                                               :fi "Nötkötti"
                                                               :sv "Skinka"}})
          app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                    :actor "alice"})]
      (is (= #{app-id} (search/find-applications "Spam")) "en title, any field")
      (is (= #{app-id} (search/find-applications "resource:Spam")) "en title")
      (is (= #{app-id} (search/find-applications "resource:Nötkötti")) "fi title")
      (is (= #{app-id} (search/find-applications "\"urn:fi:abcd\"")) "external id, any field")
      (is (= #{app-id} (search/find-applications "resource:\"urn:fi:abcd\"")) "external id, resource field")))

  (testing "find by state"
    (let [app-id (test-helpers/create-application! {:actor "alice"})]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id app-id
                              :actor "developer"
                              :comment ""})
      (is (= #{app-id} (search/find-applications "Approved")) "en status, any field")
      (is (= #{app-id} (search/find-applications "state:Approved")) "en status")
      (is (= #{app-id} (search/find-applications "state:Hyväksytty")) "fi status")))

  (testing "find by todo"
    (let [app-id (test-helpers/create-application! {:actor "alice"})]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/request-review
                              :application-id app-id
                              :actor "developer"
                              :reviewers ["elsa"]
                              :comment ""})
      (is (= #{app-id} (search/find-applications "\"Waiting for a review\"")) "en todo, any field")
      (is (= #{app-id} (search/find-applications "\"waiting-for-review\"")) "keyword todo, any field")
      (is (= #{app-id} (search/find-applications "todo:\"Waiting for a review\"")) "en todo")
      (is (= #{app-id} (search/find-applications "todo:\"Odottaa katselmointia\"")) "fi todo")
      (is (= #{app-id} (search/find-applications "todo:\"waiting-for-review\"")) "keyword todo, any field")))

  (testing "find by form content"
    (let [form-id (test-helpers/create-form! {:form/fields [{:field/id "1"
                                                             :field/type :text
                                                             :field/title {:en "Text field"
                                                                           :fi "Tekstikenttä"
                                                                           :sv "Textfält"}
                                                             :field/optional false}]})
          form-id2 (test-helpers/create-form! {:form/fields [{:field/id "1"
                                                              :field/type :text
                                                              :field/title {:en "Text field"
                                                                            :fi "Tekstikenttä"
                                                                            :sv "Textfält"}
                                                              :field/optional false}]})
          wf-id (test-helpers/create-workflow! {})
          cat-id (test-helpers/create-catalogue-item! {:form-id form-id :workflow-id wf-id})
          cat-id2 (test-helpers/create-catalogue-item! {:form-id form-id2 :workflow-id wf-id})
          app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id cat-id2]
                                                    :actor "alice"})]
      (test-helpers/command! {:type :application.command/save-draft
                              :application-id app-id
                              :actor "alice"
                              :field-values [{:form form-id
                                              :field "1"
                                              :value "Tis but a scratch."}
                                             {:form form-id2
                                              :field "1"
                                              :value "It's just a flesh wound."}]})
      (is (= #{app-id} (search/find-applications "scratch")) "any field")
      (is (= #{app-id} (search/find-applications "form:scratch")) "form field")
      (is (= #{app-id} (search/find-applications "flesh")) "any field")
      (is (= #{app-id} (search/find-applications "form:flesh")) "form field")))

  (testing "updating applications"
    (let [form-id (test-helpers/create-form! {:form/fields [{:field/id "1"
                                                             :field/type :text
                                                             :field/title {:en "Text field"
                                                                           :fi "Tekstikenttä"
                                                                           :sv "Textfält"}
                                                             :field/optional false}]})
          cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
          app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                    :actor "alice"})]
      (test-helpers/command! {:type :application.command/save-draft
                              :application-id app-id
                              :actor "alice"
                              :field-values [{:form form-id
                                              :field "1"
                                              :value "version1"}]})
      (is (= #{app-id} (search/find-applications "version1"))
          "original version is indexed")

      (test-helpers/command! {:type :application.command/save-draft
                              :application-id app-id
                              :actor "alice"
                              :field-values [{:form form-id
                                              :field "1"
                                              :value "version2"}]})
      (is (= #{} (search/find-applications "version1"))
          "should not find old versions")
      (is (= #{app-id} (search/find-applications "version2"))
          "should find the new version")))

  (testing "multiple results"
    (is (< 1 (count (search/find-applications "alice")))))

  (testing "invalid query"
    (is (= nil (search/find-applications "+")))))
