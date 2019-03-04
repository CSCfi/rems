(ns rems.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [rems.db.applications :as applications]
            [rems.db.core :refer [*db*]]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow])
  (:import [java.util UUID]))

(defn migrate-catalogue-items! [workflow-id]
  (jdbc/execute! *db* ["update catalogue_item set wfid = ?" workflow-id]))

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

    ;; use the dynamic workflow
    (jdbc/execute! *db* ["update catalogue_item_application set wfid = ? where id = ?" (:id workflow) (:id application)])
    ;; delete old events
    (jdbc/execute! *db* ["delete from application_event where appid = ?" (:id application)])

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
        "save" nil ; skip - the save-draft event is produced separately
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
        "autoapprove" (assert false "not implemented") ; TODO: migrate "autoapprove"
        "return" (applications/add-dynamic-event! {:event/type :application.event/returned
                                                   :event/time (:time event)
                                                   :event/actor (:userid event)
                                                   :application/id (:id application)
                                                   :application/comment (:comment event)})
        "review" (applications/add-dynamic-event! {:event/type :application.event/commented
                                                   :event/time (:time event)
                                                   :event/actor (:userid event)
                                                   :application/id (:id application)
                                                   ;; TODO: request-id doesn't make much sense for these old applications - make it optional?
                                                   :application/request-id (UUID. 0 0)
                                                   :application/comment (:comment event)})
        "third-party-review" (assert false "not implemented") ; TODO: migrate "third-party-review"
        "review-request" (assert false "not implemented") ; TODO: migrate "review-request"
        "withdraw" (assert false "not implemented") ; TODO: migrate "withdraw"
        "close" (assert false "not implemented"))))) ; TODO: migrate "close"
