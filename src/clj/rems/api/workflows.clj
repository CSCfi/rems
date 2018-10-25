(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications :refer [Reviewer get-reviewers]]
            [rems.api.util]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema Actor
  {:actoruserid s/Str
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(s/defschema Workflow
  {:id s/Num
   :organization s/Str
   :owneruserid s/Str
   :modifieruserid s/Str
   :title s/Str
   :final-round s/Num
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool
   :actors [Actor]})

(defn- format-workflow
  [{:keys [id organization owneruserid modifieruserid title fnlround start endt active?]}]
  {:id id
   :organization organization
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :final-round fnlround
   :start start
   :end endt
   :active active?})

(s/defschema CreateWorkflowCommand
  {:organization s/Str
   :title s/Str
   :rounds [{:type (s/enum :approval :review)
             :actors [{:userid s/Str}]}]})

(s/defschema CreateWorkflowResponse
  {:id s/Num})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableActor Reviewer)
(def get-available-actors get-reviewers)

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
      :roles #{:owner}
      :query-params [{active :- (describe s/Bool "filter active or inactive workflows") nil}]
      :return [Workflow]
      (ok (get-workflows (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create workflow"
      :roles #{:owner}
      :body [command CreateWorkflowCommand]
      :return CreateWorkflowResponse
      (ok (workflow/create-workflow! command)))

    (GET "/actors" []
      :summary "List of available actors"
      :roles #{:owner}
      :return [AvailableActor]
      (ok (get-available-actors)))))
