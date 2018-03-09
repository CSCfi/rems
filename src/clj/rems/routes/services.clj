(ns rems.routes.services
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.exception :as ex]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.entitlements :as entitlements]
            [rems.form :as form]
            [rems.locales :as locales]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]
           rems.auth.NotAuthorizedException))

(def License
  {:id s/Num
   :type s/Str
   :licensetype s/Str
   :title s/Str
   :textcontent s/Str
   :approved s/Bool})

(def Item
  {:id s/Num
   :title s/Str
   :inputprompt (s/maybe s/Str)
   :optional s/Bool
   :type s/Str
   :value (s/maybe s/Str)})

(def Event
  {:userid s/Str
   :round s/Num
   :event s/Str
   :comment (s/maybe s/Str)
   :time DateTime})

(def CatalogueItem
  {:id s/Num
   :title s/Str
   :wfid s/Num
   :formid s/Num
   :resid s/Str
   :state s/Str
   (s/optional-key :langcode) s/Keyword
   :localizations (s/maybe {s/Any s/Any})})

(def Application
  {:id (s/maybe s/Num) ;; does not exist for unsaved draft
   :formid s/Num
   :state s/Str
   :applicantuserid s/Str
   (s/optional-key :start) DateTime ;; does not exist for draft
   :wfid s/Num
   (s/optional-key :curround) s/Num ;; does not exist for draft
   (s/optional-key :fnlround) s/Num ;; does not exist for draft
   :events [Event]
   :can-approve? s/Bool
   :can-close? s/Bool
   :catalogue-items [CatalogueItem]
   :review-type (s/maybe s/Keyword)})

(def GetTranslationsResponse
  s/Any)

(def GetThemeResponse
  s/Any)

(def ExtraPage
  {s/Keyword s/Any})

(def GetConfigResponse
  {:authentication s/Keyword
   :extra-pages [ExtraPage]})

(def GetApplicationResponse
  {:id (s/maybe s/Num)
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application (s/maybe Application)
   :licenses [License]
   :title s/Str
   :items [Item]})

(def GetApplicationsResponse
  [{:id s/Num
    :catalogue-items [CatalogueItem]
    :events [Event]
    :start DateTime
    :state s/Str
    :wfid s/Num
    :fnlround s/Num
    :curround s/Num
    :applicantuserid s/Str}])

(def ValidationError s/Str)

(def SaveApplicationCommand
  {:command (s/enum "save" "submit")
   (s/optional-key :application-id) s/Num
   (s/optional-key :catalogue-items) [s/Num]
   ;; NOTE: compojure-api only supports keyword keys properly, see
   ;; https://github.com/metosin/compojure-api/issues/341
   :items {s/Keyword s/Str}
   (s/optional-key :licenses) {s/Keyword s/Str}})

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :id) s/Num
   (s/optional-key :state) s/Str
   (s/optional-key :validation) [ValidationError]})

(def JudgeApplicationCommand
  {:command (s/enum "approve" "reject" "return" "review")
   :application-id s/Num
   :round s/Num
   :comment s/Str})

(def JudgeApplicationResponse
  {:success s/Bool})

(def GetCatalogueResponse
  [CatalogueItem])

;; TODO better schema
(def Approval
  s/Any)

;; TODO better schema
(def Review
  s/Any)

(def GetActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool
   :approvals [Approval]
   :handled-approvals [Approval]
   :reviews [Review]
   :handled-reviews [Review]})

(def Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(defn longify-keys [m]
  (into {} (for [[k v] m]
             [(Long/parseLong (name k)) v])))

(defn fix-keys [application]
  (-> application
      (update-in [:items] longify-keys)
      (update-in [:licenses] longify-keys)))

(defn unauthorized-handler
  [exception ex-data request]
  (unauthorized "unauthorized"))

(defn api-judge [{:keys [command application-id round comment]}]
  (case command
    "approve" (applications/approve-application application-id round comment)
    "reject" (applications/reject-application application-id round comment)
    "return" (applications/return-application application-id round comment)
    "review" (applications/review-application application-id round comment))
  ;; failure communicated via an exception
  {:success true})

(def service-routes
  (api
   {:exceptions {:handlers {rems.auth.NotAuthorizedException (ex/with-logging unauthorized-handler)
                            ;; add logging to validation handlers
                            ::ex/request-validation (ex/with-logging ex/request-validation-handler)
                            ::ex/request-parsing (ex/with-logging ex/request-parsing-handler)
                            ::ex/response-validation (ex/with-logging ex/response-validation-handler)}}
    :swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "Sample API"
                            :description "Sample Services"}}}}

   (context "/api" []
     :tags ["translation"]

     (GET "/translations" []
       :summary     "Get translations"
       :return      GetTranslationsResponse
       (ok locales/translations)))

   (context "/api" []
     :tags ["theme"]

     (GET "/theme" []
       :summary     "Get current layout theme"
       :return      GetThemeResponse
       (ok context/*theme*)))

   (context "/api" []
     :tags ["config"]

     (GET "/config" []
       :summary     "Get configuration that is relevant to UI"
       :return      GetConfigResponse
       (ok (select-keys env [:authentication :extra-pages]))))

   (context "/api" []
     :tags ["actions"]

     (GET "/actions/" []
       :summary     "Get actions page reviewable and approvable applications"
       :return      GetActionsResponse
       (ok {:approver? true
            :reviewer? true
            :approvals (applications/get-approvals)
            :handled-approvals (applications/get-handled-approvals)
            :reviews (applications/get-applications-to-review)
            :handled-reviews (applications/get-handled-reviews)})))

   (context "/api" []
     :tags ["application"]

     (GET "/application/" []
       :summary     "Get application draft by `catalogue-items`"
       :query-params [catalogue-items :- [s/Num]]
       :return      GetApplicationResponse
       (let [app (applications/make-draft-application catalogue-items)]
         (ok (applications/get-draft-form-for app))))

     (GET "/application/:application-id" []
       :summary     "Get application by `application-id`"
       :path-params [application-id :- s/Num]
       :return      GetApplicationResponse
       (binding [context/*lang* :en]
         (ok (applications/get-form-for application-id))))

     (PUT "/application/command" []
       :summary     "Create a new application or change an existing one"
       :body        [request SaveApplicationCommand]
       :return      SaveApplicationResponse
       (ok (form/api-save (fix-keys request))))

     (PUT "/application/judge" []
        :summary "Judge an application"
        :body [request JudgeApplicationCommand]
        :return JudgeApplicationResponse
        (ok (api-judge request))))

   (context "/api" []
     :tags ["applications"]

     (GET "/applications/" []
       :summary "Get current user's all applications"
       :return GetApplicationsResponse
       (ok (applications/get-my-applications))))

   (context "/api" []
     :tags ["catalogue"]

     (GET "/catalogue/" []
       :summary "Get catalogue items"
       :return GetCatalogueResponse
       (binding [context/*lang* :en]
         (ok (catalogue/get-localized-catalogue-items)))))

   (context "/api" []
     :tags ["entitlements"]
     (GET "/entitlements/" []
       :summary "Get all entitlements"
       :return [Entitlement]
       (ok (entitlements/get-entitlements-for-api))))))
