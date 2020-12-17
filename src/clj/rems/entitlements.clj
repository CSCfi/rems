(ns rems.entitlements
  (:require [compojure.core :refer [GET defroutes]]
            [rems.api.util :as api-util]
            [rems.db.entitlements :refer [get-entitlements-for-export]]
            [ring.util.http-response :as response]))

(defroutes entitlements-routes
  (GET "/entitlements.csv" []
    (api-util/check-user)
    (response/content-type
     {:status 200
      :body (get-entitlements-for-export)}
     "text/csv")))
