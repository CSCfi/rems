(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.application-util :as application-util]
            [rems.application.commands :as commands]
            [rems.application.search :as search]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.text :refer [with-language]]
            [rems.util :refer [getx-user-id update-present]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [java.io ByteArrayInputStream]))

;; Response models

(s/defschema CreateApplicationCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema CreateApplicationResponse
  (assoc SuccessResponse
         (s/optional-key :application-id) s/Int))

(s/defschema User
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Applicant
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Commenter
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Commenters
  [Commenter])

(s/defschema Decider
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

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

(def ^:private todo-roles
  #{:handler :commenter :decider :past-commenter :past-decider})

(defn- potential-todo? [application]
  (and (some todo-roles (:application/roles application))
       (not= :application.state/draft (:application/state application))))

(defn- get-potential-todos [user-id]
  (->> (applications/get-all-applications user-id)
       (filter potential-todo?)))

(defn- todo? [application]
  (and (= :application.state/submitted (:application/state application))
       (some #{:application.command/approve
               :application.command/comment
               :application.command/decide}
             (:application/permissions application))))

(defn get-todos [user-id]
  (->> (get-potential-todos user-id)
       (filter todo?)))

(defn get-handled-todos [user-id]
  (->> (get-potential-todos user-id)
       (remove todo?)))

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
                     (applications/command!))]
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
      (ok (->> (get-todos (getx-user-id))
               (filter-with-search query))))

    (GET "/handled" []
      :summary "Get all applications that the current user no more needs to act on."
      :roles #{:logged-in}
      :return [ApplicationOverview]
      :query-params [{query :- (describe s/Str "search query") nil}]
      (ok (->> (get-handled-todos (getx-user-id))
               (filter-with-search query))))

    (POST "/create" []
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [request CreateApplicationCommand]
      :return CreateApplicationResponse
      (ok (applications/create-application! (getx-user-id) (:catalogue-item-ids request))))

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
      (let [attachment (attachments/get-attachment attachment-id)
            application-id (:application/id attachment)]
        (when application-id
          (applications/get-application (getx-user-id) application-id)) ;; check that user is allowed to read application
        (if attachment
          (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
              (content-type (:attachment/type attachment)))
          (api-util/not-found-json-response))))

    ;; TODO: think about size limit
    (POST "/add-attachment" []
      :summary "Add an attachment file related to an application"
      :roles #{:applicant}
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [application-id :- (describe s/Int "application id")]
      :middleware [multipart/wrap-multipart-params]
      :return SaveAttachmentResponse
      (let [application (applications/get-application (getx-user-id) application-id)]
        (when-not (application-util/form-fields-editable? application)
          (throw-forbidden))
        (ok (attachments/save-attachment! file (getx-user-id) application-id))))

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
    (command-endpoint :application.command/change-resources commands/ChangeResourcesCommand)
    (command-endpoint :application.command/invite-member commands/InviteMemberCommand)
    (command-endpoint :application.command/approve commands/ApproveCommand)
    (command-endpoint :application.command/close commands/CloseCommand)
    (command-endpoint :application.command/remark commands/RemarkCommand)
    (command-endpoint :application.command/comment commands/CommentCommand
                      "This corresponds to the \"Review\" operation in the UI.")
    (command-endpoint :application.command/decide commands/DecideCommand)
    (command-endpoint :application.command/reject commands/RejectCommand)
    (command-endpoint :application.command/request-comment commands/RequestCommentCommand
                      "This corresponds to the \"Request review\" operation in the UI.")
    (command-endpoint :application.command/request-decision commands/RequestDecisionCommand)
    (command-endpoint :application.command/remove-member commands/RemoveMemberCommand)
    (command-endpoint :application.command/return commands/ReturnCommand)
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

    (GET "/:application-id/pdf" []
      :summary "Get a pdf version of an application"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Int "application id")]
      :produces ["application/pdf"]
      (if-let [app (applications/get-application (getx-user-id) application-id)]
        (with-language context/*lang*
          #(-> app
               (pdf/application-to-pdf-bytes)
               (ByteArrayInputStream.)
               (ok)
               ;; could also set "attachment" here to force download:
               (header "Content-Disposition" (str "filename=\"" application-id ".pdf\""))
               (content-type "application/pdf")))
        (api-util/not-found-json-response)))))
