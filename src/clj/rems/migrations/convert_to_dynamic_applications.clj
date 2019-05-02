(ns rems.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [rems.db.applications :as applications]
            [rems.db.applications.legacy :as legacy]
            [rems.db.core :refer [*db*] :as db]
            [rems.db.workflow :as workflow]
            [rems.db.workflow-actors :as actors]
            [rems.poller.email :as email])
  (:import [java.util UUID]))

(defn migrate-catalogue-items! [workflow-id]
  (jdbc/execute! *db* ["update catalogue_item set wfid = ?" workflow-id]))

(defn migrate-application! [application-id workflow-id]
  (let [read-user (or (first (actors/get-by-role application-id "approver"))
                      ;; auto-approved workflows do not have an approver,
                      ;; so the applicant is the only one who can see the application
                      (:applicantuserid (applications/get-application-state application-id)))
        form (legacy/get-form-for read-user application-id)
        application (:application form)
        workflow (workflow/get-workflow workflow-id)
        comment-requests-by-commenter (atom {})]
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
    (applications/add-event! {:event/type :application.event/draft-saved
                              :event/time (:start application)
                              :event/actor (:applicantuserid application)
                              :application/id (:id application)
                              :application/field-values (->> (:items form)
                                                             (map (fn [item]
                                                                    [(:id item) (:value item)]))
                                                             (into {}))})
    (applications/add-event! {:event/type :application.event/licenses-accepted
                              :event/time (:start application)
                              :event/actor (:applicantuserid application)
                              :application/id (:id application)
                              :application/accepted-licenses (->> (:licenses form)
                                                                  (filter :approved)
                                                                  (map :id)
                                                                  set)})
    (doseq [event (:events application)]
      (case (:event event)
        "save" nil ; skip - the draft-saved event is produced separately and the legacy applications don't have form history
        "apply" (applications/add-event! {:event/type :application.event/submitted
                                          :event/time (:time event)
                                          :event/actor (:userid event)
                                          :application/id (:id application)})
        "reject" (applications/add-event! {:event/type :application.event/rejected
                                           :event/time (:time event)
                                           :event/actor (:userid event)
                                           :application/id (:id application)
                                           :application/comment (or (:comment event) "")})
        "approve" (applications/add-event! {:event/type :application.event/approved
                                            :event/time (:time event)
                                            :event/actor (:userid event)
                                            :application/id (:id application)
                                            :application/comment (or (:comment event) "")})
        "autoapprove" (applications/add-event! {:event/type :application.event/approved
                                                :event/time (:time event)
                                                :event/actor (:userid event)
                                                :application/id (:id application)
                                                :application/comment ""})
        "return" (applications/add-event! {:event/type :application.event/returned
                                           :event/time (:time event)
                                           :event/actor (:userid event)
                                           :application/id (:id application)
                                           :application/comment (or (:comment event) "")})
        "review" (applications/add-event! {:event/type :application.event/commented
                                           :event/time (:time event)
                                           :event/actor (:userid event)
                                           :application/id (:id application)
                                           ;; TODO: request-id doesn't make much sense for these old applications - make it optional?
                                           :application/request-id (UUID. 0 0)
                                           :application/comment (or (:comment event) "")})
        "review-request" (applications/add-event! {:event/type :application.event/comment-requested
                                                   :event/time (:time event)
                                                   ;; TODO: the review request's actor is not known; can we guess the approver?
                                                   :event/actor (:userid event)
                                                   :application/id (:id application)
                                                   :application/request-id (let [request-id (UUID/randomUUID)]
                                                                             (swap! comment-requests-by-commenter
                                                                                    assoc (:userid event) request-id)
                                                                             request-id)
                                                   :application/commenters [(:userid event)]
                                                   :application/comment (or (:comment event) "")})
        "third-party-review" (applications/add-event! {:event/type :application.event/commented
                                                       :event/time (:time event)
                                                       :event/actor (:userid event)
                                                       :application/id (:id application)
                                                       :application/request-id (get @comment-requests-by-commenter (:userid event))
                                                       :application/comment (or (:comment event) "")})
        "withdraw" (applications/add-event! {:event/type :application.event/returned
                                             :event/time (:time event)
                                             :event/actor (:userid event)
                                             :application/id (:id application)
                                             :application/comment (or (:comment event) "")})
        "close" (applications/add-event! {:event/type :application.event/closed
                                          :event/time (:time event)
                                          :event/actor (:userid event)
                                          :application/id (:id application)
                                          :application/comment (or (:comment event) "")})))))

(defn migrate-all-applications! [new-workflow-id]
  (conman/with-transaction [*db* {:isolation :serializable}]
    (let [new-workflow (workflow/get-workflow new-workflow-id)]
      (assert (= "workflow/dynamic" (get-in new-workflow [:workflow :type])))

      (migrate-catalogue-items! (:id new-workflow))
      (doseq [application (->> (db/get-applications {})
                               (remove :workflow))] ;; remove dynamic applications
        (println "Converting application" (:id application))
        (migrate-application! (:id application) (:id new-workflow)))
      (println "Marking all pending emails as sent")
      (email/mark-all-emails-as-sent!)
      (println "Done. Next go to the administration pages and archive all non-dynamic workflows."))))
