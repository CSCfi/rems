(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [deftest is]]
            [conman.core :as conman]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.events :as events]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.db.workflow-actors :as actors]
            [rems.form-validation :as form-validation]
            [rems.json :as json]
            [rems.permissions :as permissions]
            [rems.util :refer [secure-token]]
            [rems.workflow.dynamic :as dynamic]))

;;; Query functions

;; TODO use also in UI side?
(defn is-dynamic-application? [application]
  (= :workflow/dynamic (get-in application [:workflow :type])))

(defn is-dynamic-handler? [user-id application]
  (contains? (set (get-in application [:workflow :handlers])) user-id))

;;; Creating applications

(defn allocate-external-id! [prefix]
  (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
    (let [all (db/get-external-ids {:prefix prefix})
          last (apply max (cons 0 (map (comp read-string :suffix) all)))
          new (str (inc last))]
      (db/add-external-id! {:prefix prefix :suffix new})
      {:prefix prefix :suffix new})))

(defn format-external-id [{:keys [prefix suffix]}]
  (str prefix "/" suffix))

(defn application-external-id! [time]
  (let [id-prefix (str (.getYear time))]
    (format-external-id (allocate-external-id! id-prefix))))

(defn application-created-event [{:keys [application-id catalogue-item-ids time actor allocate-external-id?]}]
  (assert (seq catalogue-item-ids) "catalogue item not specified")
  (let [items (catalogue/get-localized-catalogue-items {:ids catalogue-item-ids})]
    (assert (= (count items) (count catalogue-item-ids)) "catalogue item not found")
    (assert (= 1 (count (distinct (mapv :wfid items)))) "catalogue items did not have the same workflow")
    (assert (= 1 (count (distinct (mapv :formid items)))) "catalogue items did not have the same form")
    (let [workflow-id (:wfid (first items))
          form-id (:formid (first items))
          workflow (-> (:workflow (workflow/get-workflow workflow-id))
                       (update :type keyword))
          licenses (db/get-licenses {:wfid workflow-id
                                     :items catalogue-item-ids})]
      (assert (= :workflow/dynamic (:type workflow))
              (str "workflow type was " (:type workflow))) ; TODO: support other workflows
      {:event/type :application.event/created
       :event/time time
       :event/actor actor
       :application/id application-id
       :application/external-id (when allocate-external-id? ;; TODO parameterize id allocation?
                                  (application-external-id! time))
       :application/resources (map (fn [item]
                                     {:catalogue-item/id (:id item)
                                      :resource/ext-id (:resid item)})
                                   items)
       :application/licenses (map (fn [license]
                                    {:license/id (:id license)})
                                  licenses)
       :form/id form-id
       :workflow/id workflow-id
       :workflow/type (:type workflow)})))

(defn add-application-created-event! [opts]
  (events/add-event! (application-created-event (assoc opts :allocate-external-id? true))))

(defn- get-workflow-id-for-catalogue-items [catalogue-item-ids]
  (:workflow/id (application-created-event {:catalogue-item-ids catalogue-item-ids})))

(defn create-application! [user-id catalogue-item-ids]
  (let [start (time/now)
        app-id (:id (db/create-application! {:user user-id
                                             ;; TODO: remove catalogue_item_application.wfid
                                             :wfid (get-workflow-id-for-catalogue-items catalogue-item-ids)
                                             :start start}))]
    (add-application-created-event! {:application-id app-id
                                     :catalogue-item-ids catalogue-item-ids
                                     :time start
                                     :actor user-id})
    {:success true
     :application-id app-id}))

;;; Running commands

(defn- fix-workflow-from-db [wf]
  ;; TODO could use a schema for this coercion
  (update (json/parse-string wf)
          :type keyword))

(defn get-dynamic-application-state [application-id] ; TODO: legacy code; remove me
  (let [application (or (first (db/get-applications {:id application-id}))
                        (throw (rems.InvalidRequestException.
                                (str "Application " application-id " not found"))))
        events (events/get-application-events application-id)
        application (assoc application
                           :state :application.state/draft
                           :dynamic-events events
                           :workflow (fix-workflow-from-db (:workflow application))
                           :last-modified (or (:event/time (last events))
                                              (:start application)))]
    (assert (is-dynamic-application? application) (pr-str application))
    (dynamic/apply-events application events)))

(defn- valid-user? [userid]
  (not (nil? (users/get-user-attributes userid))))

(defn- validate-form-answers [form-id answers]
  (let [form (form/get-form form-id)
        _ (assert form)
        fields (for [field (:items form)]
                 (assoc field :value (get-in answers [:items (:id field)])))]
    (form-validation/validate-fields fields)))

(def ^:private db-injections
  {:valid-user? valid-user?
   :validate-form-answers validate-form-answers
   :secure-token secure-token
   :get-catalogue-item catalogue/get-localized-catalogue-item})

(defn command! [cmd]
  (assert (:application-id cmd))
  ;; Use locks to prevent multiple commands being executed in parallel.
  ;; Serializable isolation level will already avoid anomalies, but produces
  ;; lots of transaction conflicts when there is contention. This lock
  ;; roughly doubles the throughput for rems.db.test-transactions tests.
  (jdbc/execute! db/*db* ["LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE"])
  (let [events (events/get-application-events (:application-id cmd))
        app (-> nil
                (dynamic/apply-events events)
                (model/enrich-workflow-handlers workflow/get-workflow))
        result (dynamic/handle-command cmd app db-injections)]
    (if (:success result)
      (events/add-event! (:result result))
      result)))

(defn accept-invitation [user-id invitation-token]
  (or (when-let [application-id (:id (db/get-application-by-invitation-token {:token invitation-token}))]
        (let [response (command! {:type :application.command/accept-invitation
                                  :actor user-id
                                  :application-id application-id
                                  :token invitation-token
                                  :time (time/now)})]
          (if-not response
            {:success true
             :application-id application-id}
            {:success false
             :errors (:errors response)})))
      {:success false
       :errors [{:type :t.actions.errors/invalid-token :token invitation-token}]}))
