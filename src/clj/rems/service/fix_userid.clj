
(ns rems.service.fix-userid
  (:require [clojure.tools.logging :as log]
            [rems.db.api-key]
            [rems.db.applications]
            [rems.db.attachments]
            [rems.db.blacklist]
            [rems.db.core]
            [rems.db.events]
            [rems.db.form]
            [rems.db.invitation]
            [rems.db.licenses]
            [rems.db.organizations]
            [rems.db.resource]
            [rems.db.roles]
            [rems.db.user-mappings]
            [rems.db.user-settings]
            [rems.db.users]
            [rems.db.workflow]
            [rems.service.dependencies]))

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
                              {:userid new-userid})]]]
     (do
       (apply prn #'fix-audit-log audit-log params)
       (when-not simulate?
         (apply rems.db.core/update-audit-log! params))
       {:audit-log audit-log :params params}))))

(comment
  (fix-audit-log "alice" "frank" false))

(defn fix-blacklist-event [old-userid new-userid simulate?]
  (doall
   (for [old-event (rems.db.blacklist/get-events)
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
         :let [params [{:user (:userid new)
                        :resource (:resourceid new)
                        :application (:catappid new)
                        :approvedby (:approvedby new)
                        :revokedby (:revokedby new)
                        :start (:start new)
                        :end (:end new)
                        :id (:entitlementid new)}]]]
     (do
       (apply prn #'fix-entitlement old params)
       (when-not simulate?
         (apply rems.db.core/update-entitlement! params))
       {:old old :params params}))))

(comment
  (fix-entitlement "alice" "frank" false))

;; nothing to fix in external_application_id
;; nothing to fix in form_template

(defn fix-invitation [old-userid new-userid simulate?]
  (doall
   (for [old (rems.db.invitation/get-invitations nil)
         :let [new (cond-> old
                     (= old-userid (get-in old [:invitation/invited-by :userid]))
                     (assoc-in [:invitation/invited-by :userid] new-userid)

                     (= old-userid (get-in old [:invitation/invited-user :userid]))
                     (assoc-in [:invitation/invited-user :userid] new-userid))]
         :when (not= new old)
         :let [params [new]]]
     (do
       (apply prn #'fix-invitation old params)
       (when-not simulate?
         (apply rems.db.invitation/update-invitation! params))
       {:old old :params params}))))

(comment
  (fix-invitation "alice" "frank" false))


;; nothing to fix in license
;; nothing to fix in license_attachment
;; nothing to fix in license_localization

(defn fix-organization [old-userid new-userid simulate?]
  (doall
   (for [old (rems.db.organizations/get-organizations-raw)
         :let [new (update old :organization/owners (partial mapv #(if (= old-userid (:userid %))
                                                                     {:userid new-userid}
                                                                     %)))]
         :when (not= new old)
         :let [params [new]]]
     (do
       (apply prn #'fix-organization old params)
       (when-not simulate?
         (apply rems.db.organizations/set-organization! params))
       {:old old :params params}))))

(comment
  (fix-organization "organization-owner2" "frank" false))

;; nothing to fix in outbox
;; NB: this is a table that should contain rows only momentarily


;; nothing to fix in resource
;; nothing to fix in resource_licenses


(defn fix-roles [old-userid new-userid simulate?]
  (when-let [old-roles (rems.db.roles/get-roles old-userid)]
    (apply prn #'fix-roles old-userid old-roles)
    (when-not simulate?
      (rems.db.roles/remove-roles! old-userid)
      (rems.db.roles/update-roles! new-userid old-roles))
    {:old {:userid old-userid :roles old-roles}
     :params [new-userid old-roles]}))

(comment
  (fix-roles "frank" "owner" false))

;; NB: referential constraints force use to handle
;; users, settings in one go

(defn fix-user [old-userid new-userid simulate?]
  (when (rems.db.users/user-exists? old-userid) ; referential constraints will force this to exist at least
    (let [old-user (rems.db.users/get-user old-userid)
          old-settings (rems.db.user-settings/get-user-settings old-userid)
          old-mappings (rems.db.user-mappings/get-user-mappings {:userid old-userid})]
      (apply prn #'fix-user old-user old-settings old-mappings)
      (when-not simulate?
        (rems.db.users/add-user! new-userid old-user)
        (rems.db.user-settings/update-user-settings! new-userid old-settings)
        (rems.db.user-mappings/delete-user-mapping! {:userid old-userid})
        (doseq [old-mapping old-mappings
                :when (not= (:ext-id-value old-mapping) new-userid)] ; not saved in login either
          (rems.db.user-mappings/create-user-mapping! (assoc old-mapping :userid new-userid))))
      {:old {:user old-user :settings old-settings :mappings old-mappings}
       :params [new-userid]})))

(defn remove-old-user [old-userid simulate?]
  (when-not simulate?
    (rems.db.user-settings/delete-user-settings! old-userid)
    (rems.db.users/remove-user! old-userid)))

(comment
  (rems.db.users/get-user "alice")
  (fix-user "alice" "frank" false))


(defn fix-workflow [old-userid new-userid simulate?]
  (doall
   (for [old (rems.db.workflow/get-workflows)
         :let [old {:id (:id old)
                    :organization (:organization old)
                    :title (:title old)
                    :handlers (mapv :userid (get-in old [:workflow :handlers]))}
               new (update old :handlers (partial mapv #(if (= old-userid %)
                                                          new-userid ; NB: format is different
                                                          %)))]
         :when (not= new old)
         :let [params [new]]]
     (do
       (apply prn #'fix-workflow old params)
       (when-not simulate?
         (apply rems.db.workflow/edit-workflow! params))
       {:old old :params params}))))

(comment
  (fix-workflow "bona-fide-bot" "frank" false))

;; nothing to fix in workflow_licenses

(defn fix-all [old-userid new-userid simulate?]
  (let [result (doall
                (for [f [#'fix-user ; many tables refer to user
                         #'fix-apikey
                         #'fix-application-event
                         #'fix-attachment
                         #'fix-audit-log
                         #'fix-blacklist-event
                         #'fix-entitlement
                         #'fix-invitation
                         #'fix-organization
                         #'fix-roles
                         #'fix-workflow]]
                  [(:name (meta f))
                   ;; wrap in try-catch to ensure all fixes are attempted
                   (try (f old-userid new-userid simulate?)
                        (catch Throwable exception
                          (log/error exception
                                     (.getMessage exception))))]))]
    (remove-old-user old-userid simulate?)
    ;; (rems.db.applications/reload-cache!) ; can be useful if running from REPL
    result))

(comment
  (fix-all "owner" "elsa" false)
  (fix-all "alice" "frank" false)
  (fix-all "elixir-alice" "alice" false))
