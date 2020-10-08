(ns rems.jwt
  (:require [buddy.core.keys :as buddy-keys]
            [buddy.sign.jwe :as buddy-jwe]
            [buddy.sign.jwt :as buddy-jwt]
            [clj-http.client :as http]
            [mount.core :as mount]
            [rems.common.util :refer [getx index-by]]
            [rems.config :refer [oidc-configuration]]))

(mount/defstate ^:dynamic oidc-public-keys
  :start (when-let [jwks-uri (:jwks_uri oidc-configuration)]
           (getx (http/get jwks-uri
                           {:as :json})
                 :body)))

(defn- fetch-public-key [jwt]
  (let [key-id (:kid (buddy-jwe/decode-header jwt))
        jwk (getx (index-by [:kid] (getx oidc-public-keys :keys))
                  key-id)]
    (buddy-keys/jwk->public-key jwk)))

(defn sign [claims secret & [opts]]
  (buddy-jwt/sign claims secret opts))

(defn validate [jwt issuer audience now]
  (let [public-key (fetch-public-key jwt)]
    (buddy-jwt/unsign jwt public-key (merge {:alg :rs256
                                             :now now}
                                            (when issuer {:iss issuer})
                                            (when audience {:aud audience})))))
