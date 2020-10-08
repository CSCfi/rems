(ns rems.jwt
  (:require [buddy.sign.jwt :as buddy-jwt]
            [clojure.data.json :as json]
            [mount.core :as mount]
            [rems.config :refer [oidc-configuration]])
  (:import [com.auth0.jwk JwkProviderBuilder JwkProvider]
           [com.auth0.jwt JWT JWTVerifier$BaseVerification]
           [com.auth0.jwt.algorithms Algorithm]
           [com.auth0.jwt.interfaces Clock]
           [java.net URL]
           [java.nio.charset StandardCharsets]
           [java.time Instant]
           [java.util Base64 Date]))

(mount/defstate ^:dynamic ^JwkProvider oidc-public-keys
  :start (when-let [jwks-uri (:jwks_uri oidc-configuration)]
           (-> (JwkProviderBuilder. (URL. ^String jwks-uri))
               (.build))))

(defn- fetch-public-key [^String jwt]
  (let [key-id (.getKeyId (JWT/decode jwt))]
    (.getPublicKey (.get oidc-public-keys key-id))))

(defn- decode-base64url [^String base64-str]
  (-> (Base64/getUrlDecoder)
      (.decode base64-str)
      (String. StandardCharsets/UTF_8)))

(defn sign [claims secret & [opts]]
  (buddy-jwt/sign claims secret opts))

(defn validate [^String jwt issuer audience now]
  (let [public-key (fetch-public-key jwt)
        algorithm (Algorithm/RSA256 public-key nil)
        clock (reify Clock
                (getToday [_]
                  (Date/from now)))
        verifier (JWT/require algorithm)]
    (when issuer
      (.withIssuer verifier (into-array String [issuer])))
    (when audience
      (.withAudience verifier (into-array String [audience])))
    (let [verifier (. (cast JWTVerifier$BaseVerification verifier) build clock)]
      (-> (.verify verifier jwt)
          (.getPayload)
          (decode-base64url)
          (json/read-str :key-fn keyword)))))

(defn expired?
  ([jwt]
   (expired? jwt (Instant/now)))
  ([jwt ^Instant now]
   (< (:exp jwt) (.getEpochSecond now))))
