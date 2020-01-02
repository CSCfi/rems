(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.services.attachment :as attachment]
            [rems.api.services.command :as command]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.todos :as todos]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.application.commands :as commands]
            [rems.application.search :as search]
            [rems.auth.auth :as auth]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.config :as config]
            [rems.db.applications :as applications]
            [rems.db.csv :as csv]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.util :refer [getx-user-id update-present]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import java.io.ByteArrayInputStream))

;; Response models

(s/defschema CreateApplicationCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema CreateApplicationResponse
  (assoc SuccessResponse
         (s/optional-key :application-id) s/Int))

(s/defschema Applicant
  UserWithAttributes)

(s/defschema Commenter
  UserWithAttributes)

(s/defschema Commenters
  [Commenter])

(s/defschema Decider
  UserWithAttributes)

(s/defschema Deciders
  [Decider])

(s/defschema AcceptInvitationResult
  (assoc SuccessResponse
         (s/optional-key :application-id) s/Int
         (s/optional-key :errors) [s/Any]))

(s/defschema SaveAttachmentResponse
  (assoc SuccessResponse
         (s/optional-key :id) s/Int))

(s/defschema CopyAsNewResponse
  (assoc SuccessResponse
         (s/optional-key :application-id) s/Int))

;; Api implementation

(defn- filter-with-search [query apps]
  (if (str/blank? query)
    apps
    (let [app-ids (search/find-applications query)]
      (filter (fn [app]
                (contains? app-ids (:application/id app)))
              apps))))

(defn- coerce-command-from-api [cmd]
  ;; TODO: schema could do these coercions for us
  (update-present cmd :decision keyword))

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
    `(POST ~path []
       :summary ~(str "Submit a `" (name command) "` command for an application. " additional-doc)
       :roles #{:logged-in}
       :body [request# ~schema]
       :return SuccessResponse
       (ok (api-command ~command request#)))))

(defn accept-invitation [invitation-token]
  (if-let [application-id (applications/get-application-by-invitation-token invitation-token)]
    (api-command :application.command/accept-invitation
                 {:application-id application-id
                  :token invitation-token})
    {:success false
     :errors [{:type :t.actions.errors/invalid-token :token invitation-token}]}))

(def my-applications-api
  (context "/my-applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get the current user's own applications"
      :roles #{:logged-in}
      :return [ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query") nil}]
      (ok (->> (applications/get-my-applications (getx-user-id))
               (filter-with-search query))))))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get all applications which the current user can see"
      :roles #{:logged-in}
      :return [ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query") nil}]
      (ok (->> (applications/get-all-applications (getx-user-id))
               (filter-with-search query))))

    (GET "/todo" []
      :summary "Get all applications that the current user needs to act on."
      :roles #{:logged-in}
      :return [ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query") nil}]
      (ok (->> (todos/get-todos (getx-user-id))
               (filter-with-search query))))

    (GET "/handled" []
      :summary "Get all applications that the current user no more needs to act on."
      :roles #{:logged-in}
      :return [ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query") nil}]
      (ok (->> (todos/get-handled-todos (getx-user-id))
               (filter-with-search query))))

    (POST "/create" []
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [request CreateApplicationCommand]
      :return CreateApplicationResponse
      (ok (api-command :application.command/create request)))

    (POST "/copy-as-new" []
      :summary "Create a new application as a copy of an existing application."
      :roles #{:logged-in}
      :body [request commands/CopyAsNewCommand]
      :return CopyAsNewResponse
      (ok (api-command :application.command/copy-as-new request)))

    (GET "/commenters" []
      :summary "Available third party commenters"
      :roles #{:handler}
      :return Commenters
      (ok (users/get-commenters)))

    (GET "/export" []
      :summary "Export all submitted applications of a given form as CSV"
      :roles #{:owner}
      :query-params [form-id :- (describe s/Int "form id")]
      (-> (ok (applications/export-applications-for-form-as-csv (getx-user-id) form-id))
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

    ;; TODO: think about size limit
    (POST "/add-attachment" []
      :summary "Add an attachment file related to an application"
      :roles #{:applicant}
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [application-id :- (describe s/Int "application id")]
      :middleware [multipart/wrap-multipart-params]
      :return SaveAttachmentResponse
      (ok (attachment/add-application-attachment (getx-user-id) application-id file)))

    (POST "/accept-invitation" []
      :summary "Accept an invitation by token"
      :roles #{:logged-in}
      :query-params [invitation-token :- (describe s/Str "invitation token")]
      :return AcceptInvitationResult
      (ok (accept-invitation invitation-token)))

    (command-endpoint :application.command/accept-invitation commands/AcceptInvitationCommand)
    (command-endpoint :application.command/accept-licenses commands/AcceptLicensesCommand)
    (command-endpoint :application.command/add-licenses commands/AddLicensesCommand)
    (command-endpoint :application.command/add-member commands/AddMemberCommand)
    (command-endpoint :application.command/approve commands/ApproveCommand)
    (command-endpoint :application.command/assign-external-id commands/AssignExternalIdCommand)
    (command-endpoint :application.command/change-resources commands/ChangeResourcesCommand)
    (command-endpoint :application.command/close commands/CloseCommand)
    (command-endpoint :application.command/comment commands/CommentCommand
                      "This corresponds to the \"Review\" operation in the UI.")
    (command-endpoint :application.command/decide commands/DecideCommand)
    (command-endpoint :application.command/invite-member commands/InviteMemberCommand)
    (command-endpoint :application.command/reject commands/RejectCommand)
    (command-endpoint :application.command/remark commands/RemarkCommand)
    (command-endpoint :application.command/remove-member commands/RemoveMemberCommand)
    (command-endpoint :application.command/request-comment commands/RequestCommentCommand
                      "This corresponds to the \"Request review\" operation in the UI.")
    (command-endpoint :application.command/request-decision commands/RequestDecisionCommand)
    (command-endpoint :application.command/return commands/ReturnCommand)
    (command-endpoint :application.command/revoke commands/RevokeCommand)
    (command-endpoint :application.command/save-draft commands/SaveDraftCommand)
    (command-endpoint :application.command/submit commands/SubmitCommand)
    (command-endpoint :application.command/uninvite-member commands/UninviteMemberCommand)

    ;; the path parameter matches also non-numeric paths, so this route must be after all overlapping routes
    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")]
      :responses {200 {:schema Application}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (applications/get-application (getx-user-id) application-id)]
        (ok app)
        (api-util/not-found-json-response)))

    (GET "/:application-id/pdf" request
      :summary "PDF export of application (EXPERIMENTAL)"
      :roles #{:logged-in :api-key}
      :path-params [application-id :- (describe s/Int "application id")]
      :responses {200 {}
                  501 {:schema s/Str}
                  401 {:schema s/Str}}
      (if (not (:enable-pdf-api config/env))
        (not-implemented "pdf api not enabled")
        (let [bytes (pdf/application-to-pdf (getx-user-id) (auth/get-api-key request) application-id)]
          (-> (ok (ByteArrayInputStream. bytes))
              (content-type "application/pdf")))))

    (GET "/:application-id/license-attachment/:license-id/:language" []
      :summary "Get file associated with licence of type attachment associated with application."
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")
                    license-id :- (describe s/Int "license id")
                    language :- (describe s/Keyword "language code")]
      (if-let [attachment (licenses/get-application-license-attachment (getx-user-id) application-id license-id language)]
        (attachment/download attachment)
        (api-util/not-found-json-response)))))
