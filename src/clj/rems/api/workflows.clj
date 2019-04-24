(ns rems.api.workflows
  (:require [clj-time.core :as time-core]
            [clojure.test :refer [deftest is]]
            [compojure.api.sweet :refer :all]
            [rems.api.applications :refer [User get-users]]
            [rems.api.schema :refer [SuccessResponse]]
            [rems.api.util]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime DateTimeZone]))

(def UserId s/Str)

(s/defschema Actor
  {:actoruserid UserId
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(s/defschema WorkflowLicense
  {:type s/Str
   :start DateTime
   :textcontent s/Str
   :localizations [s/Any]
   :end (s/maybe DateTime)})

(defn parse-db-time [s]
  (when s
    (-> (DateTime/parse s)
        (.withZone DateTimeZone/UTC))))

(deftest test-parse-db-time
  (is (= nil (parse-db-time nil)))
  (is (= (time-core/date-time 2019 1 30 7 56 38 627) (parse-db-time "2019-01-30T09:56:38.627616+02:00")))
  (is (= (time-core/date-time 2015 2 13 12 47 26) (parse-db-time "2015-02-13T14:47:26+02:00"))))

(defn format-license [license]
  (-> license
      (select-keys [:type :textcontent :localizations])
      (assoc :start (parse-db-time (:start license)))
      (assoc :end (parse-db-time (:end license)))))

(s/defschema Workflow
  {:id s/Num
   :organization s/Str
   :owneruserid UserId
   :modifieruserid UserId
   :title s/Str
   :final-round s/Num
   :workflow s/Any
   :start DateTime
   :end (s/maybe DateTime)
   :expired s/Bool
   :enabled s/Bool
   :archived s/Bool
   :actors [Actor]
   :licenses [WorkflowLicense]})

(s/defschema Workflows
  [Workflow])

(defn- format-workflow
  [{:keys [id organization owneruserid modifieruserid title fnlround workflow start end expired enabled archived licenses]}]
  {:id id
   :organization organization
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :final-round fnlround
   :workflow workflow
   :start start
   :end end
   :expired expired
   :enabled enabled
   :archived archived
   :licenses licenses})

(s/defschema CreateWorkflowCommand
  {:organization s/Str
   :title s/Str
   :type s/Keyword
   (s/optional-key :handlers) [UserId]})

(s/defschema UpdateWorkflowCommand
  {:id s/Num
   (s/optional-key :title) s/Str
   (s/optional-key :handlers) [UserId]
   ;; type can't change
   (s/optional-key :enabled) s/Bool
   (s/optional-key :archived) s/Bool})

(s/defschema CreateWorkflowResponse
  {:success s/Bool
   :id s/Num})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableActor User)
(s/defschema AvailableActors [AvailableActor])
(def get-available-actors get-users)

(defn- get-workflows [filters]
  (doall
   (for [wf (workflow/get-workflows filters)]
     (assoc (format-workflow wf)
            ;; TODO should this be in db.workflow?
            :actors (db/get-workflow-actors {:wfid (:id wf)})))))

(defn- get-workflow [workflow-id]
  (-> workflow-id
      workflow/get-workflow
      format-workflow
      (update :licenses #(mapv format-license %))
      (assoc :actors (db/get-workflow-actors {:wfid workflow-id}))))

(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled workflows") false}
                     {expired :- (describe s/Bool "whether to include expired workflows") false}
                     {archived :- (describe s/Bool "whether to include archived workflows") false}]
      :return Workflows
      (ok (get-workflows (merge (when-not expired {:expired false})
                                (when-not disabled {:enabled true})
                                (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create workflow"
      :roles #{:owner}
      :body [command CreateWorkflowCommand]
      :return CreateWorkflowResponse
      (ok (workflow/create-workflow! (assoc command :user-id (getx-user-id)))))

    (PUT "/update" []
      :summary "Update workflow"
      :roles #{:owner}
      :body [command UpdateWorkflowCommand]
      :return SuccessResponse
      (ok (workflow/update-workflow! command)))

    (GET "/actors" []
      :summary "List of available actors"
      :roles #{:owner}
      :return AvailableActors
      (ok (get-available-actors)))

    (GET "/:workflow-id" []
      :summary "Get workflow by id"
      :roles #{:owner}
      :path-params [workflow-id :- (describe s/Num "workflow-id")]
      :return Workflow
      (ok (get-workflow workflow-id)))))
