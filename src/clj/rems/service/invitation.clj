(ns rems.service.invitation
  (:require [rems.service.util :as util]
            [rems.db.applications]
            [rems.db.invitation]
            [rems.db.users]
            [rems.db.workflow]
            [rems.email.core :as email]
            [medley.core :refer [update-existing]]
            [rems.util :refer [secure-token]]))

(defn- join-dependencies [invitation]
  (when invitation
    (-> invitation
        (update-existing :invitation/invited-by rems.db.users/join-user)
        (update-existing :invitation/invited-user rems.db.users/join-user))))

(defn- apply-user-permissions [userid invitation]
  (dissoc invitation :invitation/token))

(defn- invalid-invitation-type-error [cmd]
  (when-not (:workflow-id cmd) ; so far we only support invitation to workflow
    {:success false
     :errors [{:type :t.accept-invitation.errors/invalid-invitation-type
               :workflow-id (:workflow-id cmd)}]}))

(defn- invalid-workflow-error [cmd]
  (when-let [workflow-id (:workflow-id cmd)]
    (if-let [workflow (rems.db.workflow/get-workflow workflow-id)]
      ;; TODO: check for workflow status, or perhaps it's ok to invite to any workflow?
      (let [organization (:organization workflow)]
        (util/check-allowed-organization! organization))
      {:success false
       :errors [{:type :t.accept-invitation.errors/invalid-workflow :workflow-id workflow-id}]})))

(defn get-invitations-full [cmd]
  (->> cmd
       rems.db.invitation/get-invitations
       (mapv join-dependencies)))

(defn get-invitations [cmd]
  (->> cmd
       get-invitations-full
       (mapv (partial apply-user-permissions (:userid cmd)))))

(defn get-invitation-full [id]
  (->> {:ids [id]}
       get-invitations-full
       first))

(defn get-invitation [id]
  (->> {:ids [id]}
       get-invitations
       first))


(defn create-invitation! [cmd]
  (or (invalid-invitation-type-error cmd)
      (invalid-workflow-error cmd)
      (let [id (rems.db.invitation/create-invitation! (merge {:invitation/name (:name cmd)
                                                              :invitation/email (:email cmd)
                                                              :invitation/token (secure-token)
                                                              :invitation/invited-by {:userid (:userid cmd)}}
                                                             (when-let [workflow-id (:workflow-id cmd)]
                                                               {:invitation/workflow {:workflow/id workflow-id}})))]
        (when id
          (email/generate-invitation-emails! (get-invitations-full {:ids [id]})))
        {:success (not (nil? id))
         :invitation/id id})))

(defn accept-invitation! [{:keys [userid token]}]
  (if-let [invitation (first (rems.db.invitation/get-invitations {:token token}))]
    (if-let [workflow-id (get-in invitation [:invitation/workflow :workflow/id])]
      (let [workflow (rems.db.workflow/get-workflow workflow-id)
            handlers (set (map :userid (get-in workflow [:workflow :handlers])))]
        (cond (contains? handlers userid)
              {:success false
               :errors [{:type :already-joined :workflow/id workflow-id}]}

              (not (:invitation/accepted invitation))
              (do
                (rems.db.workflow/edit-workflow! {:id workflow-id
                                                  :handlers (vec (conj handlers userid))})
                (rems.db.invitation/accept-invitation! userid token)
                (rems.db.applications/reload-cache!)
                {:success true
                 :invitation/workflow {:workflow/id workflow-id}})

              :else
              {:success false
               :errors [{:key :t.accept-invitation.errors/invalid-token :token token}]}))
      {:success false
       :errors [{:key :t.accept-invitation.errors/invalid-invitation-type}]})
    {:success false
     :errors [{:key :t.accept-invitation.errors/invalid-token :token token}]}))
