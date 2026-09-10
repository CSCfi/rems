(ns ^:integration rems.application.test-search
  (:require [clj-time.coerce :as time-coerce]
            [clj-time.core :as time]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [rems.application.search :as search]
            [rems.db.applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture search-index-fixture test-db-fixture]]
            [rems.service.test-data :as test-data])
  (:import [org.joda.time DateTime]))

(use-fixtures
  :once
  test-db-fixture
  search-index-fixture)

(use-fixtures :each rollback-db-fixture)

(defn query-range [field ^DateTime range-min ^DateTime range-max]
  (str field ":[" (search/->lucene-date-str range-min) " TO " (search/->lucene-date-str range-max) "]"))

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
          app (rems.db.applications/get-application app-id)
          generated (:application/generated-external-id app)
          assigned "1980/0.1234-ext5"]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/assign-external-id
                              :application-id app-id
                              :actor "developer"
                              :external-id assigned})
      (is (= #{app-id} (search/find-applications (str app-id))) "app ID, any field")
      (is (= #{app-id} (search/find-applications (str "\"" assigned "\""))) "assigned ID, any field")
      (is (= #{app-id} (search/find-applications (str "id:" app-id))) "app ID")
      (is (= #{app-id} (search/find-applications (str "id:\"" generated "\""))) "generated external ID")
      (is (= #{app-id} (search/find-applications (str "id:\"" assigned "\""))) "assigned external ID")
      (is (= #{app-id} (search/find-applications (str "id:1980"))) "fragment of assigned external ID")))

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
    (is (= nil (search/find-applications "+"))))

  (testing "query by last applying user activity"
    (let [test-time (DateTime. 1000000000000)
          next-day (time/plus test-time (time/days 1))
          prev-day (time/minus test-time (time/days 1))
          app-id (test-helpers/create-application! {:actor "alice"
                                                    :time test-time})]
      ;; for clarity, these are the string values of the dates
      (are [t expected] (= expected (search/->lucene-date-str t))
        test-time "20010909"
        next-day "20010910"
        prev-day "20010908")

      (testing "happy path"
        (are [from-time to-time] (contains?
                                  (search/find-applications (query-range "last-applying-user-activity" from-time to-time))
                                  app-id)
          test-time     next-day
          prev-day      test-time
          (DateTime. 0) test-time
          test-time     test-time
          test-time     (time/now)))

      (testing "resolution is 1 day"
        (is (contains?
             (search/find-applications (query-range "last-applying-user-activity" (time/plus test-time (time/minutes 1)) next-day))
             app-id))
        (is (contains?
             (search/find-applications (query-range "last-applying-user-activity" (time/plus test-time (time/hours 1)) next-day))
             app-id)))

      (testing "with activity"
        (let [last-activity (time/plus test-time (time/days 2))]
          (test-helpers/submit-application {:application-id app-id
                                            :actor "alice"
                                            :time last-activity})
          (is (contains?
               (search/find-applications (query-range "last-applying-user-activity" last-activity (time/plus last-activity (time/days 1))))
               app-id))
          (is (not (contains?
                    (search/find-applications (query-range "last-applying-user-activity" test-time (time/minus last-activity (time/days 1))))
                    app-id))
              "the earlier submit event is no longer the last")))))

  (testing "range query by last submitted event"
    (let [test-time (DateTime. 100000000)
          app-id (test-helpers/create-application! {:actor "alice"
                                                    :time test-time})
          test-time-2 (time/plus test-time (time/days 10))
          app-id-2 (test-helpers/create-application! {:actor "alice"
                                                      :time test-time-2})]
      (test-helpers/submit-application {:application-id  app-id
                                        :actor "alice"
                                        :time (time/plus test-time (time/days 1))})
      (test-helpers/command! {:type :application.command/return
                              :application-id app-id
                              :actor "developer"
                              :time (time/plus test-time (time/days 1) (time/minutes 1))
                              :comment ""})
      (test-helpers/submit-application {:application-id app-id
                                        :actor "alice"
                                        :time (time/plus test-time (time/days 2))})
      (test-helpers/submit-application {:application-id  app-id-2
                                        :actor "alice"
                                        :time (time/plus test-time-2 (time/days 1))})
      (test-helpers/command! {:type :application.command/return
                              :application-id app-id-2
                              :actor "developer"
                              :time (time/plus test-time-2 (time/days 1))
                              :comment ""})
      (test-helpers/submit-application {:application-id  app-id-2
                                        :actor "alice"
                                        :time (time/plus test-time-2 (time/days 2))})
      (testing "returns the first application"
        (let [query (query-range "first-submitted" test-time (time/plus test-time (time/days 3)))
              apps-in-t+3d (search/find-applications query)]
          (is (contains? apps-in-t+3d app-id)
              (str "with query: " query))
          (is (not (contains? apps-in-t+3d app-id-2))
              (str "with query: " query))))

      (testing "returns the other application"
        (let [query (query-range "first-submitted" test-time-2 (time/plus test-time-2 (time/days 3)))
              apps-in-t2+3d (search/find-applications query)]
          (is (contains? apps-in-t2+3d app-id-2)
              (str "with query: " query))
          (is (not (contains? apps-in-t2+3d app-id))
              (str "with query: " query))))

      (testing "returns both applications"
        (let [query (query-range "first-submitted" test-time (time/plus test-time-2 (time/days 3)))
              apps (search/find-applications query)]
          (is (set/subset? #{app-id app-id-2} apps)
              (str "with query: " query))))

      (testing "returns neither"
        (let [query (query-range "first-submitted" (time/plus test-time-2 (time/days 3)) (time/now))
              apps (search/find-applications query)]
          (is (empty? (set/intersection #{app-id app-id-2} apps))
              (str "with query: " query))))))

  (testing "with leap day"
    (let [app-id (test-helpers/create-application! {:actor "alice"
                                                    :time (DateTime. "2024-02-29T10:00:00")})
          app-id-2 (test-helpers/create-application! {:actor "alice"
                                                      :time (DateTime. "2024-03-01T10:00:00")})]
      (testing "with inclusive range"
        (let [query "last-activity:[20240201 TO 20240301]"
              apps (search/find-applications query)]
          (is (contains? apps app-id)
              (str "with query: " query))
          (is (contains? apps app-id-2)
              (str "with query: " query))))

      (testing "with exclusive range"
        ;; "get all applications from date x up until end of february without having to know the number of the last day"
        (let [query "last-activity:[20240201 TO 20240301}"
              apps (search/find-applications query)]
          (is (contains? apps app-id)
              (str "with query: " query))
          (is (not (contains? apps app-id-2))
              (str "with query: " query)))))))
