(ns rems.api.subscriptions
  (:require [compojure.api.sweet :refer [context describe GET]]
            [rems.api.schema :as schema]
            [rems.api.util] ; required for route :roles
            [rems.schema-base :as schema-base]
            [rems.service.application]
            [rems.subscriptions]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s])
  (:import rems.InvalidRequestException))

(s/defschema ApplicationUpdatedResponse
  {:application-id s/Int
   (s/optional-key :clients) [{:client-id s/Uuid
                               :user schema-base/UserWithAttributes
                               (s/optional-key :form-id) schema-base/FormId
                               (s/optional-key :field-id) schema-base/FieldId}]
   (s/optional-key :full-reload) s/Bool
   (s/optional-key :field-values) [{:form schema-base/FormId
                                    :field schema-base/FieldId
                                    :value schema-base/FieldValue}]
   (s/optional-key :application/attachments) [schema/ApplicationAttachment]})

(s/defschema ConnectionUpdatedResponse
  {:status (s/enum :all-quiet :updated)
   :user-id s/Str
   :client-id s/Uuid
   (s/optional-key :application-update) ApplicationUpdatedResponse})

(def subscriptions-api
  (context "/subscriptions" []
    :tags ["subscriptions"]

    (GET "/long-poll" []
      :summary "Get updates via long polling."
      :roles #{:logged-in}
      :query-params [{client-id :- (describe s/Uuid "client (tab) identity") nil}
                     {application-id :- (describe s/Int "(optional) application subscribed to") nil}]
      :responses {200 {:schema ConnectionUpdatedResponse}
                  404 {:schema s/Str :description "Not found"}}
      (if (some? client-id)
        (do
          (when application-id
            ;; check access rights to application
            (rems.service.application/get-application-for-user (getx-user-id) application-id))
          (ok (rems.subscriptions/long-poll client-id (getx-user-id) application-id)))

        (throw (InvalidRequestException. "invalid client-id"))))))
