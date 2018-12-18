(ns rems.api.actions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util]
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
      :roles #{:approver :reviewer}
      :return GetActionsResponse
      (ok {:approver? true
           :reviewer? true
           :approvals (applications/get-approvals (getx-user-id))
           :reviews (applications/get-applications-to-review (getx-user-id))}))
    (GET "/handled" []
      :summary "Get data for applications that have been acted on (for example approved or reviewed)"
      :roles #{:approver :reviewer}
      :return GetHandledActionsResponse
      (ok {:approver? true
           :reviewer? true
           :handled-approvals (applications/get-handled-approvals (getx-user-id))
           :handled-reviews (applications/get-handled-reviews (getx-user-id))}))))
