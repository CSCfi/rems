(ns rems.api.application
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.form :as form]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;; Response models

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

(defn- longify-keys [m]
  (into {} (for [[k v] m]
             [(Long/parseLong (name k)) v])))

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

(defn hide-event-comments [application user]
  (let [events (get-in application [:application :events])
        can-see-comments? (contains? (set (applications/get-handlers application)) (get-user-id))]
    (if can-see-comments?
      application
      (update-in application [:application :events] hide-sensitive-comments))))

(defn api-get-application [application-id]
  (when (not (empty? (db/get-applications {:id application-id})))
    (-> (applications/get-form-for application-id)
        (hide-event-comments (get-user-id)))))

(def application-api
  (context "/application" []
    :tags ["application"]

    (GET "/" []
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
      (ok (for [u (users/get-all-users)]
            {:userid (get u "eppn")
             :name (get u "commonName")
             :email (get u "mail")})))

    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema GetApplicationResponse}
                  404 {:schema s/Str :description "Not found"}}
      (check-user)
      (binding [context/*lang* :en]
        (if-let [app (api-get-application application-id)]
          (ok app)
          (not-found! "not found"))))

    (PUT "/save" []
      :summary "Create a new application, change an existing one or submit an application"
      :body [request SaveApplicationCommand]
      :return SaveApplicationResponse
      (check-user)
      (ok (form/api-save (fix-keys request))))

    (PUT "/judge" []
      :summary "Judge an application"
      :body [request JudgeApplicationCommand]
      :return SuccessResponse
      (check-user)
      (ok (api-judge request)))

    (PUT "/review_request" []
      :summary "Request a review"
      :body [request ReviewRequestCommand]
      :return SuccessResponse
      (check-user)
      (applications/send-review-request (:application-id request)
                                        (:round request)
                                        (:comment request)
                                        (:recipients request))
      (ok {:success true}))))
