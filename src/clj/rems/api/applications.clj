(ns rems.api.applications
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-roles check-user longify-keys]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.form :as form]
            [rems.pdf :as pdf]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [ring.swagger.upload :as upload]
            [schema.core :as s]))

;; Response models

(def GetApplicationsResponse
  [Application])

(def GetApplicationResponse
  {:id (s/maybe s/Num)
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application (s/maybe Application)
   :licenses [ApplicationLicense]
   :title s/Str
   :items [Item]})

(def SaveApplicationCommand
  {:command (s/enum "save" "submit")
   (s/optional-key :application-id) s/Num
   (s/optional-key :catalogue-items) [s/Num]
   ;; NOTE: compojure-api only supports keyword keys properly, see
   ;; https://github.com/metosin/compojure-api/issues/341
   :items {s/Keyword s/Str}
   (s/optional-key :licenses) {s/Keyword s/Str}})

(def ValidationMessage
  {:type (s/enum :item :license)
   :id s/Num
   :title {s/Keyword s/Str}
   :key s/Keyword
   :text s/Str})

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :id) s/Num
   (s/optional-key :state) s/Str
   (s/optional-key :validation) [ValidationMessage]})

(def JudgeApplicationCommand
  {:command (s/enum "approve" "close" "reject" "return" "review" "third-party-review" "withdraw")
   :application-id s/Num
   :round s/Num
   :comment s/Str})

(def ReviewRequestCommand
  {:application-id s/Num
   :round s/Num
   :comment s/Str
   :recipients [s/Str]})

(def Reviewer
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(def AddMemberCommand
  {:application-id s/Num
   :member s/Str})

;; Api implementation

(defn- api-judge [{:keys [command application-id round comment]}]
  (case command
    "approve" (applications/approve-application application-id round comment)
    "close" (applications/close-application application-id round comment)
    "reject" (applications/reject-application application-id round comment)
    "return" (applications/return-application application-id round comment)
    "review" (applications/review-application application-id round comment)
    "third-party-review" (applications/perform-third-party-review application-id round comment)
    "withdraw" (applications/withdraw-application application-id round comment))
  ;; failure communicated via an exception
  {:success true})

(defn- fix-keys [application]
  (-> application
      (update-in [:items] longify-keys)
      (update-in [:licenses] longify-keys)))

(defn- hide-sensitive-comments [events]
  (map (fn [event]
         (if (contains? #{"third-party-review" "review-request"} (:event event))
           (assoc event :comment nil) ; remove sensitive comment
           event))
       events))

(defn- hide-users [events]
  (map (fn [event]
         (assoc event :userid nil))
       events))

(defn hide-sensitive-information [application user]
  (let [is-handler? (contains? (set (applications/get-handlers (:application application))) user)]
    (if is-handler?
      application
      (-> application
          (update-in [:application :events] hide-sensitive-comments)
          (update-in [:application :events] hide-users)))))

(defn api-get-application [application-id]
  (when (not (empty? (db/get-applications {:id application-id})))
    (-> (applications/get-form-for application-id)
        (hide-sensitive-information (get-user-id)))))

(defn invalid-reviewer? [u]
  (or (str/blank? (get u "eppn"))
      (str/blank? (get u "commonName"))
      (str/blank? (get u "mail"))))

(defn get-reviewers []
  (for [u (->> (users/get-all-users)
               (remove invalid-reviewer?))]
    {:userid (get u "eppn")
     :name (get u "commonName")
     :email (get u "mail")}))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get current user's all applications"
      :return GetApplicationsResponse
      (check-user)
      (ok (applications/get-my-applications)))

    (GET "/draft" []
      :summary "Get application (draft) for `catalogue-items`"
      :query-params [catalogue-items :- (describe [s/Num] "catalogue item ids")]
      :return GetApplicationResponse
      (check-user)
      (let [app (applications/make-draft-application catalogue-items)]
        (ok (applications/get-draft-form-for app))))

    (GET "/reviewers" []
      :summary "Available third party reviewers"
      :return [Reviewer]
      (check-user)
      (check-roles :approver)
      (ok (get-reviewers)))

    (GET "/attachments/" []
      :summary "Get an attachment for a field in an application"
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      (check-user)
      (let [form (applications/get-form-for application-id)]
        (if-let [attachment (db/get-attachment {:item field-id
                                                :form (:id form)
                                                :application application-id})]
          (-> (:data attachment)
              (java.io.ByteArrayInputStream.)
              (ok)
              (content-type (:type attachment)))
          (not-found! "not found"))))

    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema GetApplicationResponse}
                  404 {:schema s/Str :description "Not found"}}
      (check-user)
      (if-let [app (api-get-application application-id)]
        (ok app)
        (not-found! "not found")))

    (GET "/:application-id/pdf" []
      :summary "Get a pdf version of an application."
      :path-params [application-id :- (describe s/Num "application id")]
      :produces ["application/pdf"]
      (check-user)
      (if-let [app (api-get-application application-id)]
        (-> app
            (pdf/application-to-pdf-bytes)
            (java.io.ByteArrayInputStream.)
            (ok)
            (content-type "application/pdf"))
        (not-found! "not found")))

    (POST "/save" []
      :summary "Create a new application, change an existing one or submit an application"
      :body [request SaveApplicationCommand]
      :return SaveApplicationResponse
      (check-user)
      (ok (form/api-save (fix-keys request))))

    (POST "/judge" []
      :summary "Judge an application"
      :body [request JudgeApplicationCommand]
      :return SuccessResponse
      (check-user)
      (ok (api-judge request)))

    (POST "/review_request" []
      :summary "Request a review"
      :body [request ReviewRequestCommand]
      :return SuccessResponse
      (check-user)
      (applications/send-review-request (:application-id request)
                                        (:round request)
                                        (:comment request)
                                        (:recipients request))
      (ok {:success true}))

    (POST "/add_member" []
      :summary "Add a member to an application"
      :body [request AddMemberCommand]
      :return SuccessResponse
      (check-user)
      ;; TODO: provide a nicer error message when user doesn't exist?
      (applications/add-member (:application-id request)
                               (:member request))
      (ok {:success true}))

    (POST "/add_attachment" []
      :summary "Add an attachment file related to an application field"
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :middleware [upload/wrap-multipart-params]
      :return SuccessResponse
      (applications/save-attachment! file application-id field-id)
      (ok {:success true}))))
