(ns rems.service.permissions
  (:require [clj-time.core :as time]
            [rems.api.util :refer [not-found-json-response]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db]
            [rems.common.roles :refer [has-roles?]]
            [rems.config :refer [env]]
            [rems.ga4gh :as ga4gh]
            [rems.util :refer [getx getx-user-id]]))

(defn get-jwks []
  (let [jwk (getx env :ga4gh-visa-public-key)]
    {:keys [jwk]}))

(defn get-user-permissions [{:keys [user expired]}]
  (cond
    (empty? user)
    (not-found-json-response)

    (not (or (has-roles? :handler :owner :organization-owner :reporter)
             (= user (getx-user-id))))
    (throw-forbidden)

    :else
    (ga4gh/entitlements->passport
     (db/get-entitlements {:user user
                           :active-at (when-not expired
                                        (time/now))}))))

