(ns rems.api.application
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.form :as form]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;; Response models

(def GetApplicationResponse
  {:id (s/maybe s/Num)
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application (s/maybe Application)
   :licenses [License]
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
  {:field s/Int
   :key s/Keyword
   :text s/Str})

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :id) s/Num
   (s/optional-key :state) s/Str
   (s/optional-key :validation) [ValidationMessage]})

(def JudgeApplicationCommand
  {:command (s/enum "approve" "reject" "return" "review")
   :application-id s/Num
   :round s/Num
   :comment s/Str})

(def JudgeApplicationResponse
  {:success s/Bool})

;; Api implementation

(defn- api-judge [{:keys [command application-id round comment]}]
  (case command
    "approve" (applications/approve-application application-id round comment)
    "reject" (applications/reject-application application-id round comment)
    "return" (applications/return-application application-id round comment)
    "review" (applications/review-application application-id round comment))
  ;; failure communicated via an exception
  {:success true})

(defn- longify-keys [m]
  (into {} (for [[k v] m]
             [(Long/parseLong (name k)) v])))

(defn- fix-keys [application]
  (-> application
      (update-in [:items] longify-keys)
      (update-in [:licenses] longify-keys)))

(defn api-get-application [application-id]
  (when (not (empty? (db/get-applications {:id application-id})))
    (applications/get-form-for application-id)))

(def application-api
  (context "/application" []
    :tags ["application"]

    (GET "/" []
      :summary "Get application (draft) for `catalogue-items`"
      :query-params [catalogue-items :- (describe [s/Num] "catalogue item ids")]
      :return GetApplicationResponse
      (let [app (applications/make-draft-application catalogue-items)]
        (ok (applications/get-draft-form-for app))))

    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema GetApplicationResponse}
                  404 {:schema s/Str :description "Not found"}}
      (binding [context/*lang* :en]
        (if-let [app (api-get-application application-id)]
          (ok app)
          (not-found! "not found"))))

    (PUT "/save" []
      :summary "Create a new application, change an existing one or submit an application"
      :body [request SaveApplicationCommand]
      :return SaveApplicationResponse
      (ok (form/api-save (fix-keys request))))

    (PUT "/judge" []
      :summary "Judge an application"
      :body [request JudgeApplicationCommand]
      :return JudgeApplicationResponse
      (ok (api-judge request)))))
