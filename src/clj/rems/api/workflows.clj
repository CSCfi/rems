(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
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
  {:title s/Str
   :rounds [{:actors [{:userid s/Str
                       :role (s/enum "reviewer" "approver")}]}]})

(defn- get-workflows [filters]
  (doall
    (for [wf (workflow/get-workflows filters)]
      (assoc (format-workflow wf)
        :actors (db/get-workflow-actors {:wfid (:id wf)})))))

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

    (PUT "/create" []
      :summary "Create workflow"
      :body [command CreateWorkflowCommand]
      ; TODO
      (prn command)
      (ok "TODO"))))
