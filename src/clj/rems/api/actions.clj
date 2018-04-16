(ns rems.api.actions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.util :refer [get-user-id]]
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
      (let [user-id (get-user-id)]
        (when-not user-id (throw-unauthorized))
        (ok {:approver? true
             :reviewer? true
             :approvals (applications/get-approvals)
             :handled-approvals (applications/get-handled-approvals)
             :reviews (applications/get-applications-to-review)
             :handled-reviews (applications/get-handled-reviews)})))))
