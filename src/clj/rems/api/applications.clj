(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.application-util :as application-util]
            [rems.application.commands :as commands]
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
  {:success s/Bool
   (s/optional-key :application-id) s/Int})

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
  {:success s/Bool
   (s/optional-key :application-id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema SaveAttachmentResponse
  (merge SuccessResponse
         {(s/optional-key :id) s/Int}))

;; Api implementation

(defn invalid-user? [u]
  (or (str/blank? (:eppn u))
      (str/blank? (:commonName u))
      (str/blank? (:mail u))))

;; XXX: Adding these attributes is not done consistently when retrieving
;;   the data for a user.
(defn format-user [u]
  {:userid (:eppn u)
   :name (:commonName u)
   :email (:mail u)})

;; TODO Filter applicant, requesting user
;;
;; XXX: Removing invalid users is not done consistently. It seems that
;;   only the following API calls are affected:
;;
;;     /applications/commenters
;;     /applications/members
;;     /applications/deciders
;;     /workflows/actors
;;
;;   For example, a user without commonName is able to log in and send an
;;   application, and the application is visible to the handler and can
;;   be approved.
(defn get-users []
  (->> (users/get-all-users)
       (remove invalid-user?)
       (map format-user)))

(def get-applicants get-users)

(def get-commenters get-users)

(def get-deciders get-users)

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

(defn- coerce-command-from-api [cmd]
  ;; TODO: schema could do these coercions for us
  (update-present cmd :decision keyword))

(defn api-command [command-type request]
  (let [command (-> request
                    (coerce-command-from-api)
                    (assoc :type command-type
                           :actor (getx-user-id)
                           :time (time/now)))
        errors (applications/command! command)]
    (if errors
      (ok {:success false
           :errors (:errors errors)})
      (ok {:success true}))))

(defmacro command-endpoint [command schema]
  (let [path (str "/" (name command))]
    `(POST ~path []
       :summary ~(str "Submit a `" (name command) "` command for an application.")
       :roles #{:logged-in}
       :body [request# ~schema]
       :return SuccessResponse
       (api-command ~command request#))))

(def my-applications-api
  (context "/my-applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get the current user's own applications"
      :roles #{:logged-in}
      :return [ApplicationOverview]
      (ok (applications/get-my-applications (getx-user-id))))))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get all applications which the current user can see"
      :roles #{:logged-in}
      :return [ApplicationOverview]
      (ok (applications/get-all-applications (getx-user-id))))

    (GET "/todo" []
      :summary "Get all applications that the current user needs to act on."
      :roles #{:logged-in}
      :return [ApplicationOverview]
      (ok (get-todos (getx-user-id))))

    (GET "/handled" []
      :summary "Get all applications that the current user no more needs to act on."
      :roles #{:logged-in}
      :return [ApplicationOverview]
      (ok (get-handled-todos (getx-user-id))))

    (POST "/create" []
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [request CreateApplicationCommand]
      :return CreateApplicationResponse
      (ok (applications/create-application! (getx-user-id) (:catalogue-item-ids request))))

    (GET "/commenters" []
      :summary "Available third party commenters"
      :roles #{:handler}
      :return Commenters
      (ok (get-commenters)))

    (GET "/members" []
      :summary "Existing REMS users available for application membership"
      :roles #{:handler}
      :return [Applicant]
      (ok (get-applicants)))

    (GET "/deciders" []
      :summary "Available deciders"
      :roles #{:handler}
      :return Deciders
      (ok (get-deciders)))

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
      (ok (applications/accept-invitation (getx-user-id) invitation-token)))

    (command-endpoint :application.command/accept-invitation commands/AcceptInvitationCommand)
    (command-endpoint :application.command/accept-licenses commands/AcceptLicensesCommand)
    (command-endpoint :application.command/add-licenses commands/AddLicensesCommand)
    (command-endpoint :application.command/add-member commands/AddMemberCommand)
    (command-endpoint :application.command/change-resources commands/ChangeResourcesCommand)
    (command-endpoint :application.command/invite-member commands/InviteMemberCommand)
    (command-endpoint :application.command/approve commands/ApproveCommand)
    (command-endpoint :application.command/close commands/CloseCommand)
    (command-endpoint :application.command/remark commands/RemarkCommand)
    (command-endpoint :application.command/comment commands/CommentCommand)
    (command-endpoint :application.command/decide commands/DecideCommand)
    (command-endpoint :application.command/reject commands/RejectCommand)
    (command-endpoint :application.command/request-comment commands/RequestCommentCommand)
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
               (content-type "application/pdf")))
        (api-util/not-found-json-response)))))
