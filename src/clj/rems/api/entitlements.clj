(ns rems.api.entitlements
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util]
            [rems.service.entitlements :refer [get-entitlements-for-api get-entitlements-for-csv-export]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [schema.core :as s]))

(s/defschema GetEntitlementsResponse
  [schema/Entitlement])

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
      (ok (get-entitlements-for-api {:user-id user
                                     :resource-ext-id resource
                                     :expired expired})))

    (GET "/csv" []
      :summary "Return entitlements as CSV"
      :roles #{:handler :reporter}
      :produces ["text/csv"]
      :responses {200 {:schema s/Str}}
      (-> (ok (get-entitlements-for-csv-export))
          (response/content-type "text/csv")))))
