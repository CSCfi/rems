(ns rems.api.services.invitation
  (:require [rems.api.services.util :as util]
            [rems.db.invitation :as invitation]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [medley.core :refer [update-existing]]
            [rems.util :refer [secure-token]]))

(defn- join-dependencies [invitation]
  (when invitation
    (-> invitation
        (update-existing :invitation/invited-by users/join-user)
        (update-existing :invitation/invited-user users/join-user))))

(defn- apply-user-permissions [userid invitation]
  (dissoc invitation :invitation/token))

(defn- invalid-invitation-type-error [cmd]
  (when-not (:workflow-id cmd) ; so far we only support invitation to workflow
    {:success false
     :errors [{:type :t.actions.errors/invalid-invitation-type ; NB: not used in UI
               :workflow-id (:workflow-id cmd)}]}))

(defn- invalid-workflow-error [cmd]
  (when-let [workflow-id (:workflow-id cmd)]
    (if-let [workflow (workflow/get-workflow workflow-id)]
      ;; TODO: check for workflow status
      (let [organization (:organization workflow)]
        (util/check-allowed-organization! organization))
      {:success false
       :errors [{:type :t.actions.errors/invalid-workflow :workflow-id workflow-id}]})))

(defn create-invitation! [cmd]
  (or (invalid-invitation-type-error cmd)
      (invalid-workflow-error cmd)
      (let [id (invitation/create-invitation! (merge {:invitation/email (:email cmd)
                                                      :invitation/token (secure-token)
                                                      :invitation/invited-by {:userid (:userid cmd)}}
                                                     (when-let [workflow-id (:workflow-id cmd)]
                                                       {:invitation/workflow {:workflow/id workflow-id}})))]
        {:success (not (nil? id))
         :id id})))


(defn get-invitations [cmd]
  (->> cmd
       invitation/get-invitations
       (mapv join-dependencies)
       (mapv (partial apply-user-permissions (:userid cmd)))))
