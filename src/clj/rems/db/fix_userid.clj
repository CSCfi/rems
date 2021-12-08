
(ns rems.db.fix-userid
  (:require [rems.db.api-key]
            [rems.db.attachments]
            [rems.db.blacklist]
            [rems.db.core]
            [rems.db.form]
            [rems.db.events]))

(defn fix-apikey [old-userid new-userid simulate?]
  (doall
   (for [api-key (rems.db.api-key/get-api-keys)
         :when (contains? (set (:users api-key)) old-userid)
         :let [params [(:apikey api-key) {:users (replace {old-userid new-userid} (:users api-key))}]]]
     (do
       (apply prn #'fix-apikey api-key params)
       (when-not simulate?
         (apply rems.db.api-key/update-api-key! params))
       {:api-key api-key :params params}))))

(comment
  (fix-apikey "alice" "charlie" false))

(defn fix-application-event [old-userid new-userid simulate?]
  (doall
   (for [old-event (rems.db.events/get-all-events-since 0)
         :let [new-event (cond-> old-event
                           (= old-userid (:event/actor old-event))
                           (assoc :event/actor new-userid)

                           (contains? (set (:application/reviewers old-event)) old-userid)
                           (update :application/reviewers #(replace {old-userid new-userid} %))

                           (contains? (set (:application/deciders old-event)) old-userid)
                           (update :application/deciders #(replace {old-userid new-userid} %))

                           (= old-userid (:application/member old-event))
                           (assoc :application/member new-userid)

                           (= old-userid (:application/applicant old-event))
                           (assoc :application/applicant new-userid))]
         :when (not= new-event old-event)
         :let [params [new-event]]]
     (do
       (apply prn #'fix-application-event old-event params)
       (when-not simulate?
         (apply rems.db.events/update-event! params))
       {:old-event old-event :params params}))))

(comment
  (fix-application-event "alice" "frank" false)
  (fix-application-event "carl" "charlie" true))

(defn fix-attachment [old-userid new-userid simulate?]
  (doall
   (for [attachment (rems.db.attachments/get-attachments)
         :when (= old-userid (:attachment/user attachment))
         :let [params [(assoc attachment :attachment/user new-userid)]]]
     (do
       (apply prn #'fix-attachment attachment params)
       (when-not simulate?
         (apply rems.db.attachments/update-attachment! params))
       {:attachment attachment :params params}))))

(comment
  (fix-attachment "alice" "frank" false))

(defn fix-audit-log [old-userid new-userid simulate?]
  (doall
   (for [audit-log (rems.db.core/get-audit-log)
         :when (= old-userid (:userid audit-log))
         :let [params [(merge audit-log
                              {:time-new (:time audit-log)
                               :path-new (:path audit-log)
                               :method-new (:method audit-log)
                               :apikey-new (:apikey audit-log)
                               :userid-new new-userid
                               :status-new (:status audit-log)})]]]
     (do
       (apply prn #'fix-audit-log audit-log params)
       (when-not simulate?
         (let [result (apply rems.db.core/update-audit-log! params)]
           (assert (= 1 (first result)) {:audit-log audit-log :params params :result result})))
       {:audit-log audit-log :params params}))))

(comment
  (fix-audit-log "alice" "frank" false))

(defn fix-blacklist-event [old-userid new-userid simulate?]
  (doall
   (for [old-event (rems.db.blacklist/get-events nil)
         :let [new-event (cond-> old-event
                           (= old-userid (:event/actor old-event))
                           (assoc :event/actor new-userid)

                           (= old-userid (:userid old-event))
                           (assoc :userid new-userid))]
         :when (not= new-event old-event)
         :let [params [new-event]]]
     (do
       (apply prn #'fix-blacklist-event old-event params)
       (when-not simulate?
         (apply rems.db.blacklist/update-event! params))
       {:old-event old-event :params params}))))

(comment
  (fix-blacklist-event "alice" "frank" false))


;; nothing to fix in catalogue_item
;; nothing to fix in catalogue_item_application
;; nothing to fix in catalogue_item_localization

(defn fix-entitlement [old-userid new-userid simulate?]
  (doall
   (for [old (rems.db.core/get-entitlements nil)
         :let [new (cond-> old
                     (= old-userid (:userid old))
                     (assoc :userid new-userid)

                     (= old-userid (:approvedby old))
                     (assoc :approvedby new-userid)

                     (= old-userid (:revokedby old))
                     (assoc :revokedby new-userid))]
         :when (not= new old)
         :let [params [(-> new
                           (assoc :user (:userid old))
                           (assoc :resource (:resourceid old))
                           (assoc :application (:catAppId old))
                           (assoc :id (:entitlementId old)))]]]
     (do
       (apply prn #'fix-entitlement old params)
       (when-not simulate?
         (apply rems.db.core/update-entitlement! params))
       {:old old :params params}))))

(comment
  (fix-entitlement "alice" "frank" false))

;; nothing to fix in external_application_id

(defn fix-form-template [old-userid new-userid simulate?]
  (doall
   (for [old (rems.db.form/get-form-templates nil)
         :let [new (cond-> old
                     (= old-userid (:form/owner old))
                     (assoc :form/owner new-userid)

                     (= old-userid (:form/modifier old))
                     (assoc :form/modifier new-userid))]
         :when (not= new old)
         :let [params [new]]]
     (do
       (apply prn #'fix-form-template old params)
       (when-not simulate?
         (apply rems.db.form/update-form-template! params))
       {:old old :params params}))))

(comment
  (fix-form-template "owner" "frank" false))

 ;; public | invitation                         | table    | rems
 ;; public | license                            | table    | rems
 ;; public | license_attachment                 | table    | rems
 ;; public | license_localization               | table    | rems
 ;; public | organization                       | table    | rems
 ;; public | outbox                             | table    | rems
 ;; public | resource                           | table    | rems
 ;; public | resource_licenses                  | table    | rems
 ;; public | roles                              | table    | rems
 ;; public | user_secrets                       | table    | rems
 ;; public | user_settings                      | table    | rems
 ;; public | users                              | table    | rems
 ;; public | workflow                           | table    | rems
 ;; public | workflow_licenses                  | table    | rems
