(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :refer [get-user-applications-v2 api-get-application-v2 api-get-application-v1]]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [longify-keys]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.util :refer [getx-user-id update-present]]
            [rems.workflow.dynamic :as dynamic]
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

(s/defschema SaveApplicationCommand
  {:command (s/enum "save" "submit")
   (s/optional-key :application-id) s/Num
   (s/optional-key :catalogue-items) [s/Num]
   ;; NOTE: compojure-api only supports keyword keys properly, see
   ;; https://github.com/metosin/compojure-api/issues/341
   :items {s/Any s/Str}
   (s/optional-key :licenses) {s/Any s/Str}})

(s/defschema ValidationMessage
  {:type s/Keyword
   (s/optional-key :field-id) s/Num
   (s/optional-key :license-id) s/Num})

(s/defschema SaveApplicationResponse
  {:success s/Bool
   (s/optional-key :id) s/Num
   (s/optional-key :state) (s/cond-pre s/Str s/Keyword) ;; HACK for dynamic applications
   (s/optional-key :errors) [ValidationMessage]})

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

(s/defschema DynamicCommand
  {:type s/Keyword
   :application-id s/Num
   s/Keyword s/Any})

(s/defschema AcceptInvitationResult
  {:success s/Bool
   (s/optional-key :application-id) s/Num
   (s/optional-key :errors) [s/Any]})

;; Api implementation

(defn- fix-keys [application]
  (-> application
      (update-in [:items] longify-keys)
      (update-in [:licenses] longify-keys)))

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
      :roles #{:approver}
      :return Commenters
      (ok (get-commenters)))

    (GET "/members" []
      :summary "Existing REMS users available for application membership"
      :roles #{:approver}
      :return [Applicant]
      (ok (get-applicants)))

    (GET "/deciders" []
      :summary "Available deciders"
      :roles #{:approver}
      :return Deciders
      (ok (get-deciders)))

    (GET "/attachments" []
      :summary "Get an attachment for a field in an application"
      :roles #{:logged-in}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      (let [user-id (getx-user-id)
            application (->> (applications/get-dynamic-application-state application-id)
                             (dynamic/assoc-possible-commands user-id))]
        (when-not (applications/may-see-application? user-id application)
          (throw-forbidden))
        (if-let [attachment (db/get-attachment {:item field-id
                                                :form (:form/id application)
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
      (applications/save-attachment! file (getx-user-id) application-id field-id)
      (ok {:success true}))

    (POST "/remove_attachment" []
      :summary "Remove an attachment file related to an application field"
      :roles #{:applicant}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :return SuccessResponse
      (applications/remove-attachment! (getx-user-id) application-id field-id)
      (ok {:success true}))

    (POST "/command" []
      :summary "Submit a command for a dynamic application"
      :roles #{:logged-in}
      :body [request DynamicCommand]
      :return SuccessResponse
      (let [cmd (assoc request :actor (getx-user-id))
            fixed (fix-command-from-api cmd)
            fixed (assoc fixed :time (time/now))
            errors (applications/dynamic-command! fixed)]
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
