(ns rems.routes.services
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.exception :as ex]
            [rems.context :as context]
            [rems.db.applications :refer [get-draft-form-for
                                          get-form-for
                                          make-draft-application]]
            [rems.db.catalogue :as catalogue]
            [rems.form :as form]
            [rems.locales :as locales]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clj-time.core :as time])
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
   :localizations (s/maybe {s/Any s/Any})
   })

(def Application
  {:id s/Num
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

(def GetApplicationResponse
  {:id s/Num
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application (s/maybe Application)
   :licenses [License]
   :title s/Str
   :items [Item]})

(def ValidationError s/Str)

(def SaveApplicationRequest
  {:operation s/Str
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

(def GetCatalogueResponse
  [CatalogueItem])

(def GetActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool})

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
     :tags ["actions"]

     (GET "/actions/" []
       :summary     "Get actions page reviewable and approvable applications"
       :return      GetActionsResponse
       (ok {:approver? true
            :reviewer? true})))

   (context "/api" []
     :tags ["application"]

     (GET "/application/" []
       :summary     "Get application draft by `catalogue-items`."
       :query-params [catalogue-items :- [s/Num]]
       :return      GetApplicationResponse
       (let [app (make-draft-application -1 catalogue-items)]
         (ok (get-draft-form-for app))))

     (GET "/application/:application-id" []
       :summary     "Get application by `application-id`"
       :path-params [application-id :- s/Num]
       :return      GetApplicationResponse
       (binding [context/*lang* :en]
         (ok (get-form-for application-id))))

     (PUT "/application" []
       :summary     "Create a new application or change an existing one"
       :body        [request SaveApplicationRequest]
       :return      SaveApplicationResponse
       (ok (form/api-save (fix-keys request)))))

   (context "/api" []
     :tags ["catalogue"]

     (GET "/catalogue/" []
       :summary "Get catalogue items"
       :return GetCatalogueResponse
       (binding [context/*lang* :en]
         (ok (catalogue/get-localized-catalogue-items)))))))
