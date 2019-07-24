(ns rems.api.entitlements
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.entitlements :as entitlements]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetEntitlementsResponse
  [Entitlement])

(def entitlements-api
  (context "/entitlements" []
    :tags ["entitlements"]

    (GET "/" []
      :summary "With proper privileges gets all entitlements, otherwise returns user's own entitlements."
      :roles #{:logged-in}
      :query-params [{user :- (describe s/Str "return entitlements for this user (optional), ignored if the user doesn't have appropriate privileges") nil}
                     {resource :- (describe s/Str "return entitlements for this resource (optional)") nil}
                     {expired :- (describe s/Bool "whether to include expired entitlements") false}]
      :return GetEntitlementsResponse
      (ok (entitlements/get-entitlements-for-api user resource expired)))))
