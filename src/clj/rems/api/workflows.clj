(ns rems.api.workflows
  (:require [clj-time.core :as time-core]
            [clojure.test :refer [deftest is]]
            [compojure.api.sweet :refer :all]
            [rems.api.applications :refer [User get-users format-user]]
            [rems.api.schema :refer [SuccessResponse UserId Workflow]]
            [rems.api.util]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.util :refer [getx-user-id update-present]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime DateTimeZone]))

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

(defn- format-workflow
  [{:keys [id organization owneruserid modifieruserid title fnlround workflow start end expired enabled archived licenses actors]}]
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
   :licenses (mapv format-license licenses)
   :actors actors})

(s/defschema CreateWorkflowCommand
  {:organization s/Str
   :title s/Str
   :type s/Keyword
   (s/optional-key :handlers) [UserId]})

(s/defschema UpdateWorkflowCommand
  {:id s/Int
   (s/optional-key :title) s/Str
   (s/optional-key :handlers) [UserId]
   ;; type can't change
   (s/optional-key :enabled) s/Bool
   (s/optional-key :archived) s/Bool})

(s/defschema CreateWorkflowResponse
  {:success s/Bool
   :id s/Int})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableActor User)
(s/defschema AvailableActors [AvailableActor])
(def get-available-actors get-users) ;; TODO move get-users to rems.db.users

(def ^:private get-user (comp format-user users/get-user-attributes))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update-present :workflow update :handlers #(mapv get-user %))
      ;; TODO should this be in db.workflow?
      (assoc :actors (db/get-workflow-actors {:wfid (:id wf)}))
      format-workflow))

(defn- get-workflows [filters]
  (mapv enrich-and-format-workflow (workflow/get-workflows filters)))

(defn- get-workflow [workflow-id]
  (-> workflow-id
      workflow/get-workflow
      enrich-and-format-workflow))

(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled workflows") false}
                     {expired :- (describe s/Bool "whether to include expired workflows") false}
                     {archived :- (describe s/Bool "whether to include archived workflows") false}]
      :return [Workflow]
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
      :path-params [workflow-id :- (describe s/Int "workflow-id")]
      :return Workflow
      (ok (get-workflow workflow-id)))))
