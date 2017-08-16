(ns rems.db.workflow-actors
  (:require [rems.db.core :as db]))

(defn add-approver! [wfid userid round]
  (db/create-workflow-actor! {:wfid wfid :actoruserid userid :role "approver" :round round}))

(defn add-reviewer! [wfid userid round]
  (db/create-workflow-actor! {:wfid wfid :actoruserid userid :role "reviewer" :round round}))
