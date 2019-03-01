(ns ^:integration rems.test.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.core :as db :refer [*db*]]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.migrations.convert-to-dynamic-applications :refer :all]
            [rems.test.api :refer [api-fixture]]))

(use-fixtures
  :once
  api-fixture)

(defn migrate-catalogue-items! [workflow-id]
  (jdbc/with-db-connection [conn *db*]
    (jdbc/execute! conn ["update catalogue_item set wfid = ?" workflow-id])))

(defn migrate-application! [application-id workflow-id]
  (let [read-user (->> (users/get-all-users)
                       (map (fn [user] (assoc user :roles (roles/get-roles (:eppn user)))))
                       (filter (fn [user] (contains? (:roles user) :approver)))
                       first
                       :eppn)
        form (applications/get-form-for read-user application-id)
        application (:application form)
        workflow (workflow/get-workflow workflow-id)]
    (assert (= "workflow/dynamic" (get-in workflow [:workflow :type])))

    (jdbc/with-db-connection [conn *db*]
      ;; use the dynamic workflow
      (jdbc/execute! conn ["update catalogue_item_application set wfid = ? where id = ?" (:id workflow) (:id application)])
      ;; delete old events
      (jdbc/execute! conn ["delete from application_event where appid = ?" (:id application)]))

    ;; TODO: migrate "autoapprove"
    ;; TODO: migrate "review"
    ;; TODO: migrate "third-party-review"
    ;; TODO: migrate "review-request"
    ;; TODO: migrate "withdraw"
    ;; TODO: migrate "close"

    (applications/add-application-created-event! {:application-id (:id application)
                                                  :catalogue-item-ids (->> (:catalogue-items application)
                                                                           (map :id))
                                                  :time (:start application)
                                                  :actor (:applicantuserid application)})
    (applications/add-dynamic-event! {:event/type :application.event/draft-saved
                                      :event/time (:start application)
                                      :event/actor (:applicantuserid application)
                                      :application/id (:id application)
                                      :application/field-values (->> (:items form)
                                                                     (map (fn [item]
                                                                            [(:id item) (:value item)]))
                                                                     (into {}))
                                      :application/accepted-licenses (->> (:licenses form)
                                                                          (filter :approved)
                                                                          (map :id)
                                                                          set)})
    (doseq [event (:events application)]
      (case (:event event)
        "apply" (applications/add-dynamic-event! {:event/type :application.event/submitted
                                                  :event/time (:time event)
                                                  :event/actor (:userid event)
                                                  :application/id (:id application)})
        "reject" (applications/add-dynamic-event! {:event/type :application.event/rejected
                                                   :event/time (:time event)
                                                   :event/actor (:userid event)
                                                   :application/id (:id application)
                                                   :application/comment (:comment event)})
        "approve" (applications/add-dynamic-event! {:event/type :application.event/approved
                                                    :event/time (:time event)
                                                    :event/actor (:userid event)
                                                    :application/id (:id application)
                                                    :application/comment (:comment event)})
        "return" (applications/add-dynamic-event! {:event/type :application.event/returned
                                                   :event/time (:time event)
                                                   :event/actor (:userid event)
                                                   :application/id (:id application)
                                                   :application/comment (:comment event)})))))

(deftest test-migration
  (let [applications (applications/get-applications-impl-batch "whatever" {})
        application (->> applications
                         (filter #(= 5 (:id %)))
                         (first))
        dynamic-workflows (->> (workflow/get-workflows {})
                               (filter #(= "workflow/dynamic" (get-in % [:workflow :type]))))
        new-workflow (first dynamic-workflows)]
    (assert application)
    (assert (= 1 (count dynamic-workflows)))

    (println "--- before ---")
    (pprint application)
    (migrate-catalogue-items! (:id new-workflow))
    (migrate-application! 1 (:id new-workflow))
    (migrate-application! 2 (:id new-workflow))
    (migrate-application! 3 (:id new-workflow))
    (migrate-application! 4 (:id new-workflow))
    (migrate-application! (:id application) (:id new-workflow))
    (println "--- after ---")
    (pprint (applications/get-application-state (:id application))))

  (let [event-id-seq (atom 45)
        next-event-id #(swap! event-id-seq inc)]

    (let [app-id 1
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "draft application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "draft application"
                                                           2 "draft application"
                                                           3 "draft application"
                                                           4 ""
                                                           5 "draft application"
                                                           6 "draft application"
                                                           7 "draft appl"
                                                           8 "draft application"}
                                :application/accepted-licenses #{1 2}}]
              :state :rems.workflow.dynamic/draft
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 2
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "applied application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "applied application"
                                                           2 "applied application"
                                                           3 "applied application"
                                                           4 ""
                                                           5 "applied application"
                                                           6 "applied application"
                                                           7 "applied ap"
                                                           8 "applied application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}]
              :state :rems.workflow.dynamic/submitted
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 3
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "rejected application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "rejected application"
                                                           2 "rejected application"
                                                           3 "rejected application"
                                                           4 ""
                                                           5 "rejected application"
                                                           6 "rejected application"
                                                           7 "rejected a"
                                                           8 "rejected application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/rejected
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for rejection"}]
              :state :rems.workflow.dynamic/rejected
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 4
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "accepted application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "accepted application"
                                                           2 "accepted application"
                                                           3 "accepted application"
                                                           4 ""
                                                           5 "accepted application"
                                                           6 "accepted application"
                                                           7 "accepted a"
                                                           8 "accepted application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/approved
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for approval"}]
              :state :rems.workflow.dynamic/approved
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))

    (let [app-id 5
          application (applications/get-application-state app-id)]
      (is (= {:id app-id
              :description "returned application"
              :applicantuserid "developer"
              :dynamic-events [{:event/type :application.event/created
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/resources [{:catalogue-item/id 2
                                                         :resource/ext-id "urn:nbn:fi:lb-201403262"}]
                                :application/licenses [{:license/id 1}
                                                       {:license/id 2}]
                                :form/id 1
                                :workflow/id 7
                                :workflow/type :workflow/dynamic
                                :workflow.dynamic/handlers #{"developer"}}
                               {:event/type :application.event/draft-saved
                                :event/actor "developer"
                                :event/time test-data/creation-time
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/field-values {1 "returned application"
                                                           2 "returned application"
                                                           3 "returned application"
                                                           4 ""
                                                           5 "returned application"
                                                           6 "returned application"
                                                           7 "returned a"
                                                           8 "returned application"}
                                :application/accepted-licenses #{1 2}}
                               {:event/type :application.event/submitted
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 2) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id}
                               {:event/type :application.event/returned
                                :event/actor "developer"
                                :event/time (-> application :dynamic-events (nth 3) :event/time)
                                :event/id (next-event-id)
                                :application/id app-id
                                :application/comment "comment for return"}]
              :state :rems.workflow.dynamic/returned
              :workflow {:type :workflow/dynamic
                         :handlers ["developer"]}}
             (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow]))))))

(comment
  (applications/get-application-state 2)
  (user/run-tests 'rems.test.migrations.convert-to-dynamic-applications))
