(ns rems.api.actions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :refer [get-open-reviews-v2 get-handled-reviews-v2]]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool
   :approvals [Application]
   :reviews [Application]})

(s/defschema GetHandledActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool
   :handled-approvals [Application]
   :handled-reviews [Application]})

(def actions-api
  (context "/actions" []
    :tags ["actions"]

    (GET "/" []
      :summary "Get actions page reviewable and approvable applications"
      :roles #{:approver :reviewer :handler :commenter :decider :past-commenter :past-decider}
      :return GetActionsResponse
      (ok {:approver? true
           :reviewer? true
           :approvals (applications/get-approvals (getx-user-id))
           :reviews (applications/get-applications-to-review (getx-user-id))}))
    (GET "/handled" []
      :summary "Get data for applications that have been acted on (for example approved or reviewed)"
      :roles #{:approver :reviewer :handler :commenter :decider :past-commenter :past-decider}
      :return GetHandledActionsResponse
      (ok {:approver? true
           :reviewer? true
           :handled-approvals (applications/get-handled-approvals (getx-user-id))
           :handled-reviews (applications/get-handled-reviews (getx-user-id))}))))

(def v2-reviews-api
  (context "/v2/reviews" []
    :tags ["reviews"]

    (GET "/open" []
      :summary "Lists applications which the user needs to review"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [V2ApplicationSummary]
      (ok (get-open-reviews-v2 (getx-user-id))))

    (GET "/handled" []
      :summary "Lists applications which the user has already reviewed"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [V2ApplicationSummary]
      (ok (get-handled-reviews-v2 (getx-user-id))))))
