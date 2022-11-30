(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.workflow :as workflow]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.application.events :as events]
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateWorkflowCommand
  {:organization schema-base/OrganizationId
   :title s/Str
   (s/optional-key :forms) [{:form/id s/Int}]
   :type (apply s/enum events/workflow-types) ; TODO: exclude master workflow?
   (s/optional-key :handlers) [schema-base/UserId]
   (s/optional-key :licenses) [schema-base/LicenseId]})

(s/defschema EditWorkflowCommand
  {:id s/Int
   (s/optional-key :organization) schema-base/OrganizationId
   ;; type can't change
   (s/optional-key :title) s/Str
   (s/optional-key :handlers) [schema-base/UserId]})

(s/defschema CreateWorkflowResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableActor schema-base/UserWithAttributes)
(s/defschema AvailableActors [AvailableActor])

(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :roles +admin-read-roles+
      :query-params [{disabled :- (describe s/Bool "whether to include disabled workflows") false}
                     {archived :- (describe s/Bool "whether to include archived workflows") false}]
      :return [schema/Workflow]
      (ok (workflow/get-workflows (merge (when-not disabled {:enabled true})
                                         (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create workflow"
      :roles +admin-write-roles+
      :body [command CreateWorkflowCommand]
      :return CreateWorkflowResponse
      (ok (workflow/create-workflow! command)))

    (PUT "/edit" []
      :summary "Edit workflow title and handlers"
      :roles +admin-write-roles+
      :body [command EditWorkflowCommand]
      :return schema/SuccessResponse
      (ok (workflow/edit-workflow! command)))

    (PUT "/archived" []
      :summary "Archive or unarchive workflow"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (ok (workflow/set-workflow-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable workflow"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (ok (workflow/set-workflow-enabled! command)))

    (GET "/actors" []
      :summary "List of available actors"
      :roles +admin-write-roles+
      :return AvailableActors
      (ok (workflow/get-available-actors)))

    (GET "/:workflow-id" []
      :summary "Get workflow by id"
      :roles +admin-read-roles+
      :path-params [workflow-id :- (describe s/Int "workflow-id")]
      :return schema/Workflow
      (if-some [wf (workflow/get-workflow workflow-id)]
        (ok wf)
        (not-found-json-response)))))
