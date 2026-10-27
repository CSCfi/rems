(ns rems.auth.dpop
  "DPoP (Demonstrating Proof of Possession, RFC 9449) support for REMS as an OAuth client.

  REMS signs a short-lived proof JWT for every request where it uses
  (or obtains) a DPoP-bound access token. The key is configured with
  `:oidc-dpop-private-key`, or generated at startup if not configured."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.jwt :as jwt]
            [buddy.core.keys :as keys]
            [buddy.sign.jwt])
  (:import [java.net URI]
           [java.nio.charset StandardCharsets]
           [java.security KeyPairGenerator MessageDigest]
           [java.security.interfaces ECPublicKey]
           [java.security.spec ECGenParameterSpec]
           [java.time Instant]
           [java.util Arrays Base64 UUID]))

(defn mode
  "Configured DPoP mode: nil, :optional or :required."
  []
  (let [value (:oidc-dpop env)]
    (case (some-> value name keyword)
      (nil :false) nil
      (:true :optional) :optional
      :required :required)))

(defn enabled? []
  (some? (mode)))

(defn dpop-token-type? [token-type]
  (= "dpop" (some-> token-type str/lower-case)))

(defn authorization-header [token-type access-token]
  (str (if (dpop-token-type? token-type) "DPoP" "Bearer") " " access-token))

(deftest test-authorization-header
  (is (= "Bearer abc" (authorization-header nil "abc")))
  (is (= "Bearer abc" (authorization-header "Bearer" "abc")))
  (is (= "DPoP abc" (authorization-header "DPoP" "abc")))
  (is (= "DPoP abc" (authorization-header "dpop" "abc"))))

;;; Keys

(defn- b64u ^String [^bytes bs]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bs))

(defn- sha256 ^bytes [^String s]
  (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s StandardCharsets/US_ASCII)))

(defn- unsigned-bytes
  "Big-endian unsigned representation of `n`, left-padded to `length` bytes."
  [^BigInteger n length]
  (let [bs (.toByteArray n)
        bs (if (and (> (alength bs) length) (zero? (aget bs 0)))
             (Arrays/copyOfRange bs 1 (alength bs))
             bs)
        padded (byte-array length)]
    (System/arraycopy bs 0 padded (- length (alength bs)) (alength bs))
    padded))

(defn- ec-p256-public-jwk [^ECPublicKey public-key]
  (let [point (.getW public-key)]
    {:kty "EC"
     :crv "P-256"
     :x (b64u (unsigned-bytes (.getAffineX point) 32))
     :y (b64u (unsigned-bytes (.getAffineY point) 32))}))

(defn- public-jwk-members [jwk]
  (case (:kty jwk)
    "EC" (select-keys jwk [:kty :crv :x :y])
    "RSA" (select-keys jwk [:kty :n :e])))

(defn- jwk->alg [jwk]
  (if-let [alg (:alg jwk)]
    (keyword (str/lower-case alg))
    (case [(:kty jwk) (:crv jwk)]
      ["EC" "P-256"] :es256
      ["EC" "P-384"] :es384
      ["EC" "P-521"] :es512
      ["RSA" nil] :rs256)))

(defn- generate-key []
  (let [generator (doto (KeyPairGenerator/getInstance "EC")
                    (.initialize (ECGenParameterSpec. "secp256r1")))
        key-pair (.generateKeyPair generator)]
    {:private-key (.getPrivate key-pair)
     :public-jwk (ec-p256-public-jwk (.getPublic key-pair))
     :alg :es256}))

(defn- load-key [jwk]
  {:private-key (keys/jwk->private-key jwk)
   :public-jwk (public-jwk-members jwk)
   :alg (jwk->alg jwk)})

(def ^:private generated-key
  (delay
    (log/info "No :oidc-dpop-private-key configured, generated an ephemeral DPoP key")
    (generate-key)))

(defn- signing-key []
  (if-let [jwk (:oidc-dpop-private-key env)]
    (load-key jwk)
    @generated-key))

(defn thumbprint
  "JWK SHA-256 thumbprint (RFC 7638) of a public JWK."
  [jwk]
  (let [members (into (sorted-map) (public-jwk-members jwk))]
    ;; the members are all strings, so this is the canonical form required by RFC 7638
    (b64u (sha256 (json/generate-string members)))))

(deftest test-thumbprint
  (testing "example from RFC 7638 section 3.1"
    (is (= "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs"
           (thumbprint {:kty "RSA"
                        :n "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
                        :e "AQAB"
                        :alg "RS256"
                        :kid "2011-04-29"}))))
  (testing "private and extra members are ignored"
    (let [jwk {:kty "EC" :crv "P-256" :x "x-value" :y "y-value"}]
      (is (= (thumbprint jwk)
             (thumbprint (assoc jwk :d "private" :kid "key-1" :use "sig")))))))

(defn jkt
  "Thumbprint of REMS's DPoP key, e.g. for the `dpop_jkt` authorization request parameter."
  []
  (thumbprint (:public-jwk (signing-key))))

;;; Proofs

(defn htu
  "The `htu` claim: the request URL without query and fragment."
  [url]
  (let [uri (URI. url)]
    (str (URI. (.getScheme uri) (.getRawAuthority uri) (.getRawPath uri) nil nil))))

(deftest test-htu
  (is (= "https://server.example.com/token" (htu "https://server.example.com/token")))
  (is (= "https://server.example.com:8443/a/b" (htu "https://server.example.com:8443/a/b?c=d#e"))))

(defn access-token-hash
  "The `ath` claim: base64url encoded SHA-256 hash of the access token."
  [access-token]
  (b64u (sha256 access-token)))

(defn proof
  "Returns a signed DPoP proof JWT for a single HTTP request.

  `:htm` is the HTTP method and `:htu` the request url. `:access-token` must
  be given when presenting an access token, and `:nonce` when the server
  has provided one."
  [{:keys [htm access-token nonce] :as request}]
  (let [{:keys [private-key public-jwk alg]} (signing-key)]
    (jwt/sign (cond-> {:jti (str (UUID/randomUUID))
                       :htm (str/upper-case (name htm))
                       :htu (htu (:htu request))
                       :iat (.getEpochSecond (Instant/now))}
                access-token (assoc :ath (access-token-hash access-token))
                nonce (assoc :nonce nonce))
              private-key
              {:alg alg
               :header {:typ "dpop+jwt"
                        :jwk public-jwk}})))

(deftest test-proof
  (let [proof-jwt (proof {:htm :post :htu "https://as.example/token?x=1" :nonce "the-nonce"})
        [header claims] (jwt/show proof-jwt)
        public-key (keys/jwk->public-key (:jwk header))]
    (is (= {:typ "dpop+jwt" :alg "ES256" :jwk (:public-jwk (signing-key))} header))
    (is (not (contains? (:jwk header) :d)) "no private key material")
    (is (= {:htm "POST" :htu "https://as.example/token" :nonce "the-nonce"}
           (select-keys claims [:htm :htu :nonce :ath])))
    (is (string? (:jti claims)))
    (is (<= (- (.getEpochSecond (Instant/now)) 5) (:iat claims) (.getEpochSecond (Instant/now))))
    (is (= claims (buddy.sign.jwt/unsign proof-jwt public-key {:alg :es256}))
        "signature is valid")
    (is (= (jkt) (thumbprint (:jwk header)))))
  (testing "with access token"
    (let [[_header claims] (jwt/show (proof {:htm :get :htu "https://rs.example/" :access-token "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU"}))]
      ;; example from RFC 9449 section 4.3, figure 7
      (is (= "fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo" (:ath claims)))
      (is (not (contains? claims :nonce))))))

;;; Nonces (RFC 9449 sections 8 and 9)

(defonce ^:private nonces (atom {}))

(defn- origin [url]
  (let [uri (URI. url)]
    (str (.getScheme uri) "://" (.getRawAuthority uri))))

(defn- response-header [response header]
  ;; clj-http headers are case-insensitive, but plain maps (e.g. in tests) are not
  (or (get-in response [:headers header])
      (some (fn [[k v]] (when (.equalsIgnoreCase header (name k)) v))
            (:headers response))))

(defn- nonce-challenge? [response]
  (and (contains? #{400 401} (:status response))
       (some? (response-header response "DPoP-Nonce"))
       (or (some-> (response-header response "WWW-Authenticate") (str/includes? "use_dpop_nonce"))
           (some-> (:body response) str (str/includes? "use_dpop_nonce")))))

(defn with-nonce-retry
  "Calls `(send! nonce)` with the latest nonce known for the origin of `url`.
  If the server responds with a `use_dpop_nonce` challenge, retries once with
  the new nonce. `send!` must not throw on HTTP error statuses."
  [url send!]
  (let [remember! (fn [response]
                    (when-let [nonce (response-header response "DPoP-Nonce")]
                      (swap! nonces assoc (origin url) nonce))
                    response)
        response (remember! (send! (get @nonces (origin url))))]
    (if (nonce-challenge? response)
      (remember! (send! (get @nonces (origin url))))
      response)))

(deftest test-with-nonce-retry
  (reset! nonces {})
  (let [calls (atom [])
        send! (fn [nonce]
                (swap! calls conj nonce)
                (if (= "n1" nonce)
                  {:status 200 :headers {"DPoP-Nonce" "n2"}}
                  {:status 400
                   :headers {"DPoP-Nonce" "n1"}
                   :body "{\"error\":\"use_dpop_nonce\"}"}))]
    (is (= 200 (:status (with-nonce-retry "https://as.example/token" send!))))
    (is (= [nil "n1"] @calls))
    (testing "latest nonce is reused"
      (reset! calls [])
      (with-nonce-retry "https://as.example/userinfo" send!)
      (is (= ["n2" "n1"] @calls))))
  (testing "retries only once"
    (let [calls (atom 0)]
      (is (= 401 (:status (with-nonce-retry "https://rs.example/"
                            (fn [_]
                              (swap! calls inc)
                              {:status 401
                               :headers {"WWW-Authenticate" "DPoP error=\"use_dpop_nonce\""
                                         "DPoP-Nonce" "again"}})))))
      (is (= 2 @calls))))
  (reset! nonces {}))
