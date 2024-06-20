(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [medley.core :refer [update-existing]]
            [rems.api.schema :as schema]
            [rems.service.attachment :as attachment]
            [rems.service.command :as command]
            [rems.service.licenses :as licenses]
            [rems.service.todos :as todos]
            [rems.api.util :as api-util :refer [extended-logging]] ; required for route :roles
            [rems.application.commands :as commands]
            [rems.application.search :as search]
            [rems.auth.auth :as auth]
            [rems.common.roles :refer [+admin-read-roles+]]
            [rems.config :as config]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.csv :as csv]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.schema-base :as schema-base]
            [rems.text :refer [with-language]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import java.io.ByteArrayInputStream))

;; Response models

(s/defschema CreateApplicationCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema CreateApplicationResponse
  (assoc schema/SuccessResponse
         (s/optional-key :application-id) s/Int))

(s/defschema Applicant
  schema-base/UserWithAttributes)

(s/defschema Reviewer
  schema-base/UserWithAttributes)

(s/defschema Reviewers
  [Reviewer])

(s/defschema Decider
  schema-base/UserWithAttributes)

(s/defschema Deciders
  [Decider])

(s/defschema AcceptInvitationResult
  (assoc schema/SuccessResponse
         (s/optional-key :application-id) s/Int
         (s/optional-key :errors) [s/Any]))

(s/defschema SaveAttachmentResponse
  (assoc schema/SuccessResponse
         (s/optional-key :id) s/Int))

(s/defschema CopyAsNewResponse
  (assoc schema/SuccessResponse
         (s/optional-key :application-id) s/Int))

(s/defschema ValidateRequest
  (assoc commands/CommandBase
         :field-values [{:form schema-base/FormId
                         :field schema-base/FieldId
                         :value schema-base/FieldValue}]
         (s/optional-key :duo-codes) [schema-base/DuoCode]))

(s/defschema Count
  s/Int)

(defn- overview-only-active-handlers [app]
  (update-in app
             [:application/workflow :workflow.dynamic/handlers]
             #(filter :handler/active? %)))

;; Api implementation

(def last-activity (comp time-coerce/to-long :application/last-activity))

(defn- filter-with-search [query apps]
  (if (str/blank? query)
    apps
    (let [app-ids (search/find-applications query)]
      (filter (fn [app]
                (contains? app-ids (:application/id app)))
              apps))))

(defn- coerce-command-from-api [cmd]
  ;; TODO: schema could do these coercions for us
  (update-existing cmd :decision keyword))

(defn parse-command [request command-type]
  (-> request
      (coerce-command-from-api)
      (assoc :type command-type
             :actor (getx-user-id)
             :time (time/now))))

(defn api-command [command-type request]
  (let [response (-> request
                     (parse-command command-type)
                     (command/command!))]
    (-> response
        (assoc :success (not (:errors response)))
        ;; hide possibly sensitive events, but allow other explicitly returned data
        (dissoc :events))))

(defmacro command-endpoint [command schema & [additional-doc]]
  (let [path (str "/" (name command))]
    `(POST ~path ~'request
       :summary ~(str "Submit a `" (name command) "` command for an application. " additional-doc)
       :roles #{:logged-in}
       :body [body# ~schema]
       :return schema/SuccessResponse
       (extended-logging ~'request)
       (ok (api-command ~command body#)))))

(defn accept-invitation [invitation-token]
  (if-let [application-id (applications/get-application-by-invitation-token invitation-token)]
    (api-command :application.command/accept-invitation
                 {:application-id application-id
                  :token invitation-token})
    {:success false
     :errors [{:type :t.actions.errors/invalid-token :token invitation-token}]}))

(defn validate-application [request]
  (let [application (applications/get-application-for-user (getx-user-id) (:application-id request))]
    (merge {:success true}
           (commands/validate-application application (:field-values request)))))

(defn- get-handled-applications [{:keys [query only-active-handlers limit]}]
  (time
   (cond->> (todos/get-handled-todos (getx-user-id))
     only-active-handlers (map overview-only-active-handlers)
     query (filter-with-search query)
     true (sort-by last-activity >)
     limit (take limit))))

(def my-applications-api
  (context "/my-applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get the current user's own applications"
      :roles #{:logged-in}
      :return [schema/ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query [documentation](https://github.com/CSCfi/rems/blob/master/docs/search.md)") nil}]
      (ok (->> (applications/get-my-applications (getx-user-id))
               (filter-with-search query))))))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get all applications which the current user can see"
      :roles #{:logged-in}
      :return [schema/ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query [documentation](https://github.com/CSCfi/rems/blob/master/docs/search.md)") nil}]
      (ok (->> (applications/get-all-applications (getx-user-id))
               (filter-with-search query))))

    (GET "/todo" []
      :summary "Get all applications that the current user needs to act on."
      :roles #{:logged-in}
      :return [schema/ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query [documentation](https://github.com/CSCfi/rems/blob/master/docs/search.md)") nil}]
      (ok (->> (todos/get-todos (getx-user-id))
               (filter-with-search query))))

    (GET "/handled/count" []
      :summary "Get count of all applications that the current user no more needs to act on."
      :roles #{:logged-in}
      :return Count
      (ok (todos/get-handled-todos-count (getx-user-id))))

    (GET "/handled" []
      :summary "Get all applications that the current user no more needs to act on."
      :roles #{:logged-in}
      ;; XXX: checking this schema can be mighty slow (thousands of applications to check individually)
      :return [schema/ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query [documentation](https://github.com/CSCfi/rems/blob/master/docs/search.md)") nil}
                     {only-active-handlers :- (describe s/Bool "return only workflow handlers that are active making a smaller result") false}
                     {limit :- (describe s/Int "how many results to return") nil}]
      (ok (get-handled-applications {:query query
                                     :only-active-handlers only-active-handlers
                                     :limit limit})))

    (POST "/create" request
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [command CreateApplicationCommand]
      :return CreateApplicationResponse
      (extended-logging request)
      (ok (api-command :application.command/create command)))

    (POST "/copy-as-new" request
      :summary "Create a new application as a copy of an existing application."
      :roles #{:logged-in}
      :body [request commands/CopyAsNewCommand]
      :return CopyAsNewResponse
      (extended-logging request)
      (ok (api-command :application.command/copy-as-new request)))

    (GET "/reviewers" []
      :summary "Available reviewers"
      :roles #{:handler}
      :return Reviewers
      (ok (users/get-reviewers)))

    (GET "/export" []
      :summary "Export all submitted applications of a given form as CSV"
      :roles #{:owner :reporter}
      :query-params [form-id :- (describe s/Int "form id")]
      (-> (ok (applications/export-applications-for-form-as-csv (getx-user-id)
                                                                form-id
                                                                (:language (user-settings/get-user-settings (getx-user-id)))))
          (header "Content-Disposition" (str "filename=\"" (csv/applications-filename) "\""))
          (content-type "text/csv")))

    (GET "/members" []
      :summary "Existing REMS users available for application membership"
      :roles #{:handler}
      :return [Applicant]
      (ok (users/get-applicants)))

    (GET "/deciders" []
      :summary "Available deciders"
      :roles #{:handler}
      :return Deciders
      (ok (users/get-deciders)))

    (GET "/attachment/:attachment-id" []
      :summary "Get an attachment"
      :roles #{:logged-in}
      :path-params [attachment-id :- (describe s/Int "attachment id")]
      (if-let [attachment (attachment/get-application-attachment (getx-user-id) attachment-id)]
        (attachment/download attachment)
        (api-util/not-found-json-response)))

    (POST "/add-attachment" request
      :summary "Add an attachment file related to an application"
      :roles #{:logged-in}
      :multipart-params [file :- schema/FileUpload]
      :query-params [application-id :- (describe s/Int "application id")]
      :return SaveAttachmentResponse
      (extended-logging request)
      (ok (attachment/add-application-attachment (getx-user-id) application-id file)))

    (POST "/accept-invitation" request
      :summary "Accept an invitation by token"
      :roles #{:logged-in}
      :query-params [invitation-token :- (describe s/Str "invitation token")]
      :return AcceptInvitationResult
      (extended-logging request)
      (ok (accept-invitation invitation-token)))

    (POST "/validate" request
      :summary "Validate the form, like in save, but nothing is saved. NB: At the moment, both errors and validations are identical, but this may not always be so."
      :roles #{:logged-in}
      :body [request ValidateRequest]
      :return schema/SuccessResponse
      (extended-logging request) ; this is for completeness, nothing should be saved
      (ok (validate-application request)))

    (GET "/commands" []
      :summary "List of application commands"
      :roles +admin-read-roles+
      :return [s/Keyword]
      (ok (sort commands/command-names)))

    (command-endpoint :application.command/accept-invitation commands/AcceptInvitationCommand)
    (command-endpoint :application.command/accept-licenses commands/AcceptLicensesCommand)
    (command-endpoint :application.command/add-licenses commands/AddLicensesCommand)
    (command-endpoint :application.command/add-member commands/AddMemberCommand)
    (command-endpoint :application.command/approve commands/ApproveCommand)
    (command-endpoint :application.command/assign-external-id commands/AssignExternalIdCommand)
    (command-endpoint :application.command/change-resources commands/ChangeResourcesCommand)
    (command-endpoint :application.command/change-processing-state commands/ChangeProcessingStateCommand)
    (command-endpoint :application.command/close commands/CloseCommand)
    (command-endpoint :application.command/decide commands/DecideCommand)
    (command-endpoint :application.command/delete commands/DeleteCommand "Only drafts can be deleted. Only applicants can delete drafts.")
    (command-endpoint :application.command/invite-decider commands/InviteDeciderCommand)
    (command-endpoint :application.command/invite-member commands/InviteMemberCommand)
    (command-endpoint :application.command/invite-reviewer commands/InviteReviewerCommand)
    (command-endpoint :application.command/change-applicant commands/ChangeApplicantCommand "Promote member of application to applicant. Previous applicant becomes a member.")
    (command-endpoint :application.command/redact-attachments commands/RedactAttachmentsCommand)
    (command-endpoint :application.command/reject commands/RejectCommand)
    (command-endpoint :application.command/remark commands/RemarkCommand)
    (command-endpoint :application.command/remove-member commands/RemoveMemberCommand)
    (command-endpoint :application.command/request-decision commands/RequestDecisionCommand)
    (command-endpoint :application.command/request-review commands/RequestReviewCommand)
    (command-endpoint :application.command/return commands/ReturnCommand)
    (command-endpoint :application.command/review commands/ReviewCommand)
    (command-endpoint :application.command/revoke commands/RevokeCommand)
    (command-endpoint :application.command/save-draft commands/SaveDraftCommand)
    (command-endpoint :application.command/submit commands/SubmitCommand)
    (command-endpoint :application.command/uninvite-member commands/UninviteMemberCommand)
    (command-endpoint :application.command/vote commands/VoteCommand)

    ;; the path parameter matches also non-numeric paths, so this route must be after all overlapping routes
    (GET "/:application-id" []
      :summary "Get application by `application-id`. Application is customized for the requesting user (e.g. event visibility, permissions, etc)."
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")]
      :responses {200 {:schema schema/Application}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (applications/get-application-for-user (getx-user-id) application-id)]
        (ok app)
        (api-util/not-found-json-response)))

    (GET "/:application-id/raw" []
      :summary "Get application by `application-id`. Unlike the /api/applications/:application-id endpoint, the data here isn't customized for the requesting user (see schema for details). Suitable for integrations and exporting applications."
      :roles #{:reporter :owner}
      :path-params [application-id :- (describe s/Int "application id")]
      :responses {200 {:schema schema/ApplicationRaw}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (applications/get-application application-id)]
        (ok app)
        (api-util/not-found-json-response)))

    (GET "/:application-id/attachments" []
      :summary "Get attachments for an application as a zip file"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")]
      :query-params [{all :- (describe s/Bool "Defaults to true. If set to false, the zip will only contain latest application attachments: no previous versions of attachments, and no event attachments.") true}]
      :responses {200 {}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (applications/get-application-for-user (getx-user-id) application-id)]
        (attachment/zip-attachments app all)
        (api-util/not-found-json-response)))

    (GET "/:application-id/pdf" []
      :summary "Get a pdf version of an application"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")]
      :produces ["application/pdf"]
      (if-let [app (applications/get-application-for-user (getx-user-id) application-id)]
        (with-language context/*lang*
          (-> app
              (pdf/application-to-pdf-bytes)
              (ByteArrayInputStream.)
              (ok)
              ;; could also set "attachment" here to force download:
              (header "Content-Disposition" (str "filename=\"" application-id ".pdf\""))
              (content-type "application/pdf")))
        (api-util/not-found-json-response)))

    (GET "/:application-id/license-attachment/:license-id/:language" []
      :summary "Get file associated with licence of type attachment associated with application."
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")
                    license-id :- (describe s/Int "license id")
                    language :- (describe s/Keyword "language code")]
      (if-let [attachment (licenses/get-application-license-attachment (getx-user-id) application-id license-id language)]
        (attachment/download attachment)
        (api-util/not-found-json-response)))))
