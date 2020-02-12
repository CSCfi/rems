(ns rems.api.permissions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.entitlements :as entitlements]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetPermissionsResponse
  [Ga4ghVisa])

(def permissions-api
  (context "/permissions" []
    :tags ["permissions"]

    (GET "/" []
      :summary "Returns user's permissions."
      :roles #{:logged-in}
      :query-params [{user :- (describe s/Str "return permissions for this user (optional), ignored if the user doesn't have appropriate privileges") nil}
                     {expired :- (describe s/Bool "whether to include expired permissions") false}]
      :return GetPermissionsResponse
      (ok (entitlements/get-entitlements-for-permissions-api user nil expired)))))
