(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer [SuccessResponse ArchivedCommand EnabledCommand UserId UserWithAttributes Workflow]]
            [rems.api.services.workflow :as workflow]
            [rems.api.util :as api-util]
            [rems.api.util] ; required for route :roles
            [rems.application.events :as events]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateWorkflowCommand
  {:organization s/Str
   :title s/Str
   :type (apply s/enum events/workflow-types) ; TODO: exclude master workflow?
   (s/optional-key :handlers) [UserId]})

(s/defschema EditWorkflowCommand
  {:id s/Int
   ;; type can't change
   (s/optional-key :title) s/Str
   (s/optional-key :handlers) [UserId]})

(s/defschema CreateWorkflowResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableActor UserWithAttributes)
(s/defschema AvailableActors [AvailableActor])

(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :roles #{:owner :organization-owner :handler}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled workflows") false}
                     {archived :- (describe s/Bool "whether to include archived workflows") false}]
      :return [Workflow]
      (ok (workflow/get-workflows (merge (when-not disabled {:enabled true})
                                         (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create workflow"
      :roles #{:owner :organization-owner}
      :body [command CreateWorkflowCommand]
      :return CreateWorkflowResponse
      (ok (workflow/create-workflow! (assoc command :user-id (getx-user-id)))))

    (PUT "/edit" []
      :summary "Edit workflow title and handlers"
      :roles #{:owner}
      :body [command EditWorkflowCommand]
      :return SuccessResponse
      (ok (workflow/edit-workflow! command)))

    (PUT "/archived" []
      :summary "Archive or unarchive workflow"
      :roles #{:owner}
      :body [command ArchivedCommand]
      :return SuccessResponse
      (ok (workflow/set-workflow-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable workflow"
      :roles #{:owner}
      :body [command EnabledCommand]
      :return SuccessResponse
      (ok (workflow/set-workflow-enabled! command)))

    (GET "/actors" []
      :summary "List of available actors"
      :roles #{:owner}
      :return AvailableActors
      (ok (workflow/get-available-actors)))

    (GET "/:workflow-id" []
      :summary "Get workflow by id"
      :roles #{:owner :organization-owner :handler}
      :path-params [workflow-id :- (describe s/Int "workflow-id")]
      :return Workflow
      (if-some [wf (workflow/get-workflow workflow-id)]
        (ok wf)
        (api-util/not-found-json-response)))))
