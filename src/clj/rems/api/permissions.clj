(ns rems.api.permissions
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer [ok]]
            [rems.api.util] ; required for :roles with compojure-api
            [rems.config :refer [env]]
            [rems.service.permissions :refer [get-jwks get-user-permissions]]
            [schema.core :as s])
  (:import [rems NotImplementedException]))

(defn- permissions-api-not-enabled-error []
  (when-not (:enable-permissions-api env)
    (throw (NotImplementedException. "Permissions API is not enabled. #{:enable-permissions-api}"))))

(s/defschema GetJWKSResponse
  {:keys [s/Any]})

(s/defschema GetPermissionsResponse
  {:ga4gh_passport_v1 [s/Str]})

(def permissions-api
  (routes
   (context "/" []
     :tags ["permissions"]
     :responses {501 {:description "Permissions API is not enabled. #{:enable-permissions-api}"}}
     (GET "/jwk" []
       :summary "Experimental. Get JSON Web Key Set (JWKS) (RFC 7517) containing the keys used for signing GA4GH Visas."
       :return GetJWKSResponse
       (or (permissions-api-not-enabled-error)
           (ok (get-jwks))))
     (GET "/permissions/:user" []
       ;; We're trying to replicate https://github.com/CSCfi/elixir-rems-proxy/#get-permissionsusername here
       :summary (str "Experimental. Returns user's permissions in ga4gh visa format. "
                     "Handlers, owners and reporters can query anybody's permissions. Other users can query their own permissions. "
                     "See also https://github.com/CSCfi/rems/blob/master/docs/ga4gh-visas.md")
       :roles #{:logged-in}
       :path-params [user :- (describe s/Str "return permissions for this user, required")]
       :query-params [{expired :- (describe s/Bool "whether to include expired permissions") false}]
       :return GetPermissionsResponse
       (or (permissions-api-not-enabled-error)
           (ok (get-user-permissions {:user user
                                      :expired expired})))))))
