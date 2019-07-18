(ns rems.db.legacy-workflow-actors
  (:require [rems.db.core :as db]))

(defn add-approver!
  "Adds an approver to a round of a given workflow"
  [wfid userid round]
  (db/create-workflow-actor! {:wfid wfid :actoruserid userid :role "approver" :round round}))

(defn add-reviewer!
  "Adds a reviewer to a round of a given workflow"
  [wfid userid round]
  (db/create-workflow-actor! {:wfid wfid :actoruserid userid :role "reviewer" :round round}))

(defn get-by-role
  "Returns a structure containing actoruserids.
   [application-id role]: Gets all the possible actors with the specified role that are set as actors in the workflow rounds the given application contains.
   [application-id round role]: Gets all the actors that have been defined for the specified workflow round in the given application."
  ([application-id role]
   (map :actoruserid (db/get-actors-for-applications {:application application-id :role role})))
  ([application-id round role]
   (map :actoruserid (db/get-actors-for-applications {:application application-id :round round :role role}))))
