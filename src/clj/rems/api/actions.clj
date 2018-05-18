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
   :handled-approvals [Application]
   :reviews [Application]
   :handled-reviews [Application]})

(def actions-api
  (context "/actions" []
    :tags ["actions"]

    (GET "/" []
      :summary "Get actions page reviewable and approvable applications"
      :return GetActionsResponse
      (check-user)
      (ok {:approver? true
           :reviewer? true
           :approvals (applications/get-approvals)
           :handled-approvals (applications/get-handled-approvals)
           :reviews (applications/get-applications-to-review)
           :handled-reviews (applications/get-handled-reviews)}))))
