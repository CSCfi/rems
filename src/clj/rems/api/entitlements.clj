(ns rems.api.entitlements
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util]
            [rems.common.roles :refer [has-roles? +admin-read-roles+]]
            [rems.service.entitlements :refer [get-entitlements-for-api get-entitlements-for-csv-export]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [schema.core :as s]))

(defn- getx-query-user
  "Returns user which entitlements are queried for. If current user is privileged,
   user-id can be any user id or empty (meaning query every user). Otherwise return
   current user or throw."
  [user-id]
  (let [privilege-roles +admin-read-roles+]
    (if (apply has-roles? privilege-roles)
      user-id
      (getx-user-id))))

(def entitlements-api
  (context "/entitlements" []
    :tags ["entitlements"]

    (GET "/" []
      :summary "With proper privileges gets all entitlements, otherwise returns user's own entitlements."
      :roles #{:logged-in}
      :query-params [{user :- (describe s/Str "return entitlements for this user (optional), ignored if the user doesn't have appropriate privileges") nil}
                     {resource :- (describe s/Str "return entitlements for this resource (optional)") nil}
                     {expired :- (describe s/Bool "whether to include expired entitlements") false}]
      :return [schema/Entitlement]
      (let [entitlements (get-entitlements-for-api {:user-id (getx-query-user user)
                                                    :resource-ext-id resource
                                                    :expired expired})]
        (ok entitlements)))

    (GET "/export-csv" []
      :summary "Return entitlements as CSV string. With proper privileges gets all entitlements, otherwise returns user's own entitlements."
      :roles #{:handler :reporter}
      :query-params [{user :- (describe s/Str "return entitlements for this user (optional), ignored if the user doesn't have appropriate privileges") nil}
                     {resource :- (describe s/Str "return entitlements for this resource (optional)") nil}
                     {expired :- (describe s/Bool "whether to include expired entitlements") true}
                     {separator :- (describe s/Str "which separator to use in returned csv (optional)") ","}]
      :produces ["text/csv"]
      :return s/Str
      (let [entitlements (get-entitlements-for-csv-export {:user-id (getx-query-user user)
                                                           :resource-ext-id resource
                                                           :expired expired
                                                           :separator separator})]
        (-> (ok entitlements)
            (response/content-type "text/csv"))))))
