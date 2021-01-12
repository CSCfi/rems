(ns rems.jwt
  (:require [buddy.core.keys :as buddy-keys]
            [buddy.sign.jwe :as buddy-jwe]
            [buddy.sign.jwt :as buddy-jwt]
            [clj-http.client :as http]
            [mount.core :as mount]
            [rems.common.util :refer [getx index-by]]
            [rems.config :refer [oidc-configuration]]))

;; Could consider caching this if it is a performance bottleneck.
;; However our OIDC login already has like 3 roundtrips to the OIDC
;; server so one more won't hurt that much. We will need to fetch new
;; keys occasionally in case REMS is running over an OIDC key
;; rotation.
(defn- fetch-jwks []
  (when-let [jwks-uri (:jwks_uri oidc-configuration)]
    (getx (http/get jwks-uri {:as :json}) :body)))

(defn- indexed-jwks []
  (index-by [:kid] (getx (fetch-jwks) :keys)))

(defn- fetch-public-key [jwt]
  (let [key-id (:kid (buddy-jwe/decode-header jwt))
        jwk (getx (indexed-jwks) key-id)]
    (buddy-keys/jwk->public-key jwk)))

(defn sign [claims secret & [opts]]
  (buddy-jwt/sign claims secret opts))

(defn validate [jwt issuer audience now]
  (let [public-key (fetch-public-key jwt)]
    (buddy-jwt/unsign jwt public-key (merge {:alg :rs256
                                             :now now}
                                            (when issuer {:iss issuer})
                                            (when audience {:aud audience})))))
