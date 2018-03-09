(ns rems.api.application
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.context :as context]
            [rems.db.applications :as applications]
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

(def ValidationError s/Str)

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

(def application-api
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
       (ok (api-judge request)))))
