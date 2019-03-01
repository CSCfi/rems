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

    ;; TODO: migrate "save" or actually just the form values
    ;; TODO: migrate "apply"
    ;; TODO: migrate "approve"
    ;; TODO: migrate "autoapprove"
    ;; TODO: migrate "reject"
    ;; TODO: migrate "return"
    ;; TODO: migrate "review"
    ;; TODO: migrate "third-party-review"
    ;; TODO: migrate "review-request"
    ;; TODO: migrate "withdraw"
    ;; TODO: migrate "close"

    (applications/add-application-created-event! {:application-id (:id application)
                                                  :catalogue-item-ids (->> (:catalogue-items application)
                                                                           (map :id))
                                                  :time (:start application)
                                                  :actor (:applicantuserid application)})))

(deftest test-migration
  (let [applications (applications/get-applications-impl-batch "whatever" {})
        application (->> applications
                         (filter #(= 1 (:id %)))
                         (first))
        dynamic-workflows (->> (workflow/get-workflows {})
                               (filter #(= "workflow/dynamic" (get-in % [:workflow :type]))))
        new-workflow (first dynamic-workflows)]
    (assert application)
    (assert (= 1 (count dynamic-workflows)))

    (println "--- before ---")
    (pprint application)
    (migrate-catalogue-items! (:id new-workflow))
    (migrate-application! (:id application) (:id new-workflow))
    (println "--- after ---")
    (pprint (applications/get-application-state (:id application))))

  (let [application (applications/get-application-state 1)]
    (is (= {:id 1
            :description "draft application",
            :applicantuserid "developer",
            :dynamic-events [{:event/type :application.event/created,
                              :event/actor "developer",
                              :event/time test-data/creation-time
                              :event/id 46,
                              :application/id 1,
                              :application/resources [{:catalogue-item/id 2,
                                                       :resource/ext-id "urn:nbn:fi:lb-201403262"}],
                              :application/licenses [{:license/id 1}
                                                     {:license/id 2}],
                              :form/id 1,
                              :workflow/id 7,
                              :workflow/type :workflow/dynamic,
                              :workflow.dynamic/handlers #{"developer"}}]
            :state :rems.workflow.dynamic/draft,
            :workflow {:type :workflow/dynamic,
                       :handlers ["developer"]}}
           (select-keys application [:id :description :applicantuserid :dynamic-events :state :workflow])))))




