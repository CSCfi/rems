(ns rems.api.services.invitation
  (:require [rems.api.services.util :as util]
            [rems.db.invitation :as invitation]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.email.core :as email]
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
     :errors [{:type :errors/invalid-invitation-type
               :workflow-id (:workflow-id cmd)}]}))

(defn- invalid-workflow-error [cmd]
  (when-let [workflow-id (:workflow-id cmd)]
    (if-let [workflow (workflow/get-workflow workflow-id)]
      ;; TODO: check for workflow status, or perhaps it's ok to invite to any workflow?
      (let [organization (:organization workflow)]
        (util/check-allowed-organization! organization))
      {:success false
       :errors [{:type :errors/invalid-workflow :workflow-id workflow-id}]})))

(defn get-invitations-full [cmd]
  (->> cmd
       invitation/get-invitations
       (mapv join-dependencies)))

(defn get-invitations [cmd]
  (->> cmd
       get-invitations-full
       (mapv (partial apply-user-permissions (:userid cmd)))))


(defn create-invitation! [cmd]
  (or (invalid-invitation-type-error cmd)
      (invalid-workflow-error cmd)
      (let [id (invitation/create-invitation! (merge {:invitation/name (:name cmd)
                                                      :invitation/email (:email cmd)
                                                      :invitation/token (secure-token)
                                                      :invitation/invited-by {:userid (:userid cmd)}}
                                                     (when-let [workflow-id (:workflow-id cmd)]
                                                       {:invitation/workflow {:workflow/id workflow-id}})))]
        (when id
          (email/generate-invitation-emails! (get-invitations-full {:ids [id]})))
        {:success (not (nil? id))
         :invitation/id id})))

(comment
  (binding [rems.context/*user* {:eppn "owner"}
            rems.context/*roles* #{:owner}]
    (create-invitation! {:userid "owner"
                         :email "dorothy.vaughan@nasa.gov"
                         :workflow-id 1}))
  (get-invitations nil))
