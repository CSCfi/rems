(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [rems.util :refer [get-user-id]]
            [rems.db.workflow-actors :as actors])
  (:import [org.joda.time DateTime]))

(def Actor
  {:actoruserid s/Str
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(def Workflow
  {:id s/Num
   :prefix s/Str
   :owneruserid s/Str
   :modifieruserid s/Str
   :title s/Str
   :final-round s/Num
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool
   :actors [Actor]})

(defn- format-workflow
  [{:keys [id prefix owneruserid modifieruserid title fnlround start endt active?]}]
  {:id id
   :prefix prefix
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :final-round fnlround
   :start start
   :end endt
   :active active?})

(def CreateWorkflowCommand
  {:prefix s/Str
   :title s/Str
   :rounds [{:actors [{:userid s/Str
                       :role (s/enum "reviewer" "approver")}]}]})

(defn- get-workflows [filters]
  (doall
    (for [wf (workflow/get-workflows filters)]
      (assoc (format-workflow wf)
        :actors (db/get-workflow-actors {:wfid (:id wf)})))))

(defn create-workflow [{:keys [prefix title rounds]}]
  (let [wfid (:id (db/create-workflow! {:prefix prefix,
                                        :owneruserid (get-user-id),
                                        :modifieruserid (get-user-id),
                                        :title title,
                                        :fnlround (dec (count rounds))}))]
    (doseq [[round-index round] (map-indexed vector rounds)]
      (doseq [{:keys [role userid]} (:actors round)]
        (case role
          "approver" (actors/add-approver! wfid userid round-index)
          "reviewer" (actors/add-reviewer! wfid userid round-index))))
    {:id wfid}))



(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :query-params [{active :- (describe s/Bool "filter active or inactive workflows") nil}]
      :return [Workflow]
      (check-user)
      (check-roles :owner)
      (ok (get-workflows (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create workflow"
      :body [command CreateWorkflowCommand]
      (check-user)
      (check-roles :owner)
      (ok (create-workflow command)))))
