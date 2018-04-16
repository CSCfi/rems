(ns rems.api.entitlements
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.entitlements :as entitlements]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(def entitlements-api
  (context "/entitlements" []
           :tags ["entitlements"]

           (GET "/" []
                :summary "Get all entitlements"
                :query-params [{user :- (describe s/Str "return entitlements for this user (optional)") nil}
                               {resource :- (describe s/Str "return entitlements for this resource (optional)") nil}]
                :return [Entitlement]
                (ok (entitlements/get-entitlements-for-api user resource)))))
