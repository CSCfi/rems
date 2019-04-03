(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :refer [get-user-applications-v2 api-get-application-v2 api-get-application-v1]]
            [rems.api.schema :refer :all]
            [rems.application.commands :as commands]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.util :refer [getx-user-id update-present]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import [java.io ByteArrayInputStream]))

;; Response models

(s/defschema CreateApplicationCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema CreateApplicationResponse
  {:success s/Bool
   (s/optional-key :application-id) s/Int})

(s/defschema Phases
  [{:phase s/Keyword
    (s/optional-key :active?) s/Bool
    (s/optional-key :approved?) s/Bool
    (s/optional-key :closed?) s/Bool
    (s/optional-key :completed?) s/Bool
    (s/optional-key :rejected?) s/Bool
    :text s/Keyword}])

(s/defschema GetApplicationResponse
  {:id (s/maybe s/Num)
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Keyword s/Str})
   :application (s/maybe Application)
   :licenses [ApplicationLicense]
   :accepted-licenses (s/maybe {s/Str #{s/Num}})
   :phases Phases
   :title s/Str
   :items [Item]})

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

(s/defschema Command
  ;; luckily dispatch-on compiles into a x-oneOf swagger definition, which is exactly what we want
  (apply r/dispatch-on
         ;; we need to manually coerce :type to keyword since the schema coercion hasn't happened yet
         (fn [v] (keyword (:type v)))
         (flatten (seq commands/command-schemas))))

(s/defschema AcceptInvitationResult
  {:success s/Bool
   (s/optional-key :application-id) s/Num
   (s/optional-key :errors) [s/Any]})

;; Api implementation

(defn invalid-user? [u]
  (or (str/blank? (:eppn u))
      (str/blank? (:commonName u))
      (str/blank? (:mail u))))

(defn format-user [u]
  {:userid (:eppn u)
   :name (:commonName u)
   :email (:mail u)})

;; TODO Filter applicant, requesting user
(defn get-users []
  (->> (users/get-all-users)
       (remove invalid-user?)
       (map format-user)))

(def get-applicants get-users)

(def get-commenters get-users)

(def get-deciders get-users)

(defn- check-attachment-content-type
  "Checks that content-type matches the allowed ones listed on the UI side:
   .pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
  [content-type]
  (when-not (or (#{"application/pdf"
                   "application/msword"
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                   "application/vnd.ms-powerpoint"
                   "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                   "text/plain"}
                 content-type)
                (.startsWith content-type "image/"))
    (throw (rems.InvalidRequestException. (str "Unsupported content-type: " content-type)))))

(defn- fix-command-from-api
  [cmd]
  ;; schema could do these coercions for us...
  (update-present cmd :decision keyword))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

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

    (GET "/attachments" []
      :summary "Get an attachment for a field in an application"
      :roles #{:logged-in}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      (let [user-id (getx-user-id)
            application (api-get-application-v2 user-id application-id)
            form-id (get-in application [:application/form :form/id])]
        (if-let [attachment (db/get-attachment {:item field-id
                                                :form form-id
                                                :application application-id})]
          (do (check-attachment-content-type (:type attachment))
              (-> (:data attachment)
                  (ByteArrayInputStream.)
                  (ok)
                  (content-type (:type attachment))))
          (not-found! "not found"))))

    (POST "/accept-invitation" []
      :summary "Accept an invitation by token"
      :roles #{:logged-in}
      :query-params [invitation-token :- (describe s/Str "invitation token")]
      :return AcceptInvitationResult
      (ok (applications/accept-invitation (getx-user-id) invitation-token)))

    (GET "/:application-id/pdf" []
      :summary "Get a pdf version of an application"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Num "application id")]
      :produces ["application/pdf"]
      (if-let [app (api-get-application-v1 (getx-user-id) application-id)]
        (-> app
            (pdf/application-to-pdf-bytes)
            (ByteArrayInputStream.)
            (ok)
            (content-type "application/pdf"))
        (not-found! "not found")))

    ;; TODO: think about size limit
    (POST "/add_attachment" []
      :summary "Add an attachment file related to an application field"
      :roles #{:applicant}
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :middleware [multipart/wrap-multipart-params]
      :return SuccessResponse
      (check-attachment-content-type (:content-type file))
      (attachments/save-attachment! file (getx-user-id) application-id field-id)
      (ok {:success true}))

    (POST "/remove_attachment" []
      :summary "Remove an attachment file related to an application field"
      :roles #{:applicant}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :return SuccessResponse
      (attachments/remove-attachment! (getx-user-id) application-id field-id)
      (ok {:success true}))

    (POST "/command" []
      :summary "Submit a command for an application"
      :roles #{:logged-in}
      :body [request Command]
      :return SuccessResponse
      (let [command (-> request
                        (fix-command-from-api)
                        (assoc :actor (getx-user-id))
                        (assoc :time (time/now)))
            errors (applications/command! command)]
        (if errors
          (ok {:success false
               :errors (:errors errors)})
          (ok {:success true}))))))

(def v2-applications-api
  (context "/v2/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get current user's all applications"
      :roles #{:logged-in}
      :return [V2ApplicationOverview]
      (ok (get-user-applications-v2 (getx-user-id))))

    (POST "/create" []
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [request CreateApplicationCommand]
      :return CreateApplicationResponse
      (ok (applications/create-application! (getx-user-id) (:catalogue-item-ids request))))

    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema V2Application}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (api-get-application-v2 (getx-user-id) application-id)]
        (ok app)
        (not-found! "not found")))

    (GET "/:application-id/migration" []
      :summary "Get application by `application-id` in v1 schema"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema GetApplicationResponse}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (api-get-application-v1 (getx-user-id) application-id)]
        (ok app)
        (not-found! "not found")))))
