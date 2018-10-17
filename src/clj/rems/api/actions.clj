(ns rems.api.actions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.applications :as applications]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def GetActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool
   :approvals [Application]
   :reviews [Application]})

(def GetHandledActionsResponse
  {:approver? s/Bool
   :reviewer? s/Bool
   :handled-approvals [Application]
   :handled-reviews [Application]})

(def actions-api
  (context "/actions" []
    :tags ["actions"]

    (GET "/" []
      :summary "Get actions page reviewable and approvable applications (roles: approver, reviewer)"
      :return GetActionsResponse
      (check-user)
      (ok {:approver? true
           :reviewer? true
           :approvals (applications/get-approvals)
           :reviews (applications/get-applications-to-review)}))
    (GET "/handled" []
      :summary "Get data for applications that have been acted on (for example approved or reviewed) (roles: approver, reviewer)"
      :return GetHandledActionsResponse
      (check-user)
      (ok {:approver? true
           :reviewer? true
           :handled-approvals (applications/get-handled-approvals)
           :handled-reviews (applications/get-handled-reviews)}))))
