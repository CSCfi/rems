(ns rems.auth.backend
  (:require [rems.config :refer [env]]
            [buddy.auth.protocols :as proto]
            [rems.integration.shibbo-utils :as shibbo]))

(defn- handle-unauthorized-default
  "A default response constructor for an unauthorized request."
  [_]
  {:status 401 :headers {"Content-Type" "application/json"}
   :body "{\"status\": 401, \"detail\": \"Unauthorized\"}"})

(defn haka-login-valid? [shibbo-vals]
  (let [user-ids #{"eppn"}
        ids-in-shibbo (clojure.set/intersection user-ids (set (keys shibbo-vals)))
        has-id (not (empty? ids-in-shibbo))]
    has-id))

(defn shibbo-backend
  []
  (reify
    proto/IAuthentication
    (-parse [_ request]
            (shibbo/get-attributes request env))
    (-authenticate [_ request shib-attribs]
                   (let [id
                         (if (haka-login-valid? shib-attribs) shib-attribs nil)]
                     id))))

(defn authz-backend
  []
  (reify
    proto/IAuthorization
    (-handle-unauthorized [_ request metadata]
                          (handle-unauthorized-default request))))
