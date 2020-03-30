(ns rems.api.permissions
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.db.entitlements :as entitlements]
            [rems.config :as config]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetPermissionsResponse
  Ga4ghVisa)

(def permissions-api
  (context "/permissions" []
    :tags ["permissions"]

    (GET "/:user" []
      :summary "Returns user's permissions in ga4gh visa format. Currently signed with fake key. See https://github.com/ga4gh-duri/ga4gh-duri.github.io/"
      :roles #{:handler :owner}
      :path-params [user :- (describe s/Str "return permissions for this user, required")]
      :query-params [{expired :- (describe s/Bool "whether to include expired permissions") false}]
      :return GetPermissionsResponse
      (if (not (:enable-permissions-api config/env))
        (not-implemented "permissions api not implemented")
        (do (when-not (empty? user)
              (api-util/not-found-json-response))
            (ok (entitlements/get-entitlements-for-permissions-api user nil expired)))))))
