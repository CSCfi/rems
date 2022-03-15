(ns rems.ga4gh
  "Implementation of GA4GH Passports and Visas
   <https://github.com/ga4gh-duri/ga4gh-duri.github.io/tree/master/researcher_ids>

   See also docs/ga4gh-visas.md"
  (:require [buddy.core.keys :as keys]
            [clj-time.coerce]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [rems.common.util :refer [getx]]
            [rems.config :refer [env oidc-configuration]]
            [rems.jwt :as jwt]
            [schema.core :as s])
  (:import java.time.Instant))

;; Schemas

(s/defschema VisaType
  ;; Official types from:
  ;; https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md#ga4gh-standard-passport-visa-type-definitions
  ;; We could also support custom types.
  (s/enum "AffiliationAndRole" "AcceptedTermsAndPolicies" "ResearcherStatus" "ControlledAccessGrants" "LinkedIdentities"))

(s/defschema VisaObject
  {:type VisaType
   :asserted s/Int
   :value s/Str
   :source s/Str
   (s/optional-key :conditions) s/Any ;; complex spec, not needed for REMS
   (s/optional-key :by) (s/enum "self" "peer" "system" "so" "dac")})

;; https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#conformance-for-embedded-token-issuers
(s/defschema VisaClaim
  {:iss s/Str
   :sub s/Str
   :exp s/Int
   :iat s/Int
   (s/optional-key :scope) s/Str ; Embedded Document Tokens "MUST NOT contain 'openid'"
   (s/optional-key :jti) s/Str
   :ga4gh_visa_v1 VisaObject})

;; Creating visas

(defn- visa-header []
  {:jku (str (:public-url env) "api/jwk")
   :typ "JWT"
   :kid (get-in env [:ga4gh-visa-private-key :kid])})

(defn- sign-visa [visa]
  (let [key (:ga4gh-visa-private-key env)
        parsed-key (keys/jwk->private-key key)]
    (assert key ":ga4gh-visa-private-key config variable not set!")
    ;; TODO look up algorithm from key?
    (jwt/sign (s/validate VisaClaim visa) parsed-key {:alg :rs256 :header (visa-header)})))

(def +default-length+ (time/years 1))

(defn- entitlement->visa-claims [{:keys [resid _catappid start end _mail userid _approvedby dac-id]}]
  {:iss (:public-url env)
   :sub userid
   :iat (clj-time.coerce/to-epoch (time/now))
   :exp (clj-time.coerce/to-epoch (or end (time/plus (time/now) +default-length+)))
   :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                   :value (str resid)
                   :source (or dac-id (:public-url env))
                   :by "dac" ; the Data Access Commitee acts via REMS
                   :asserted (clj-time.coerce/to-epoch start)}})

(defn- entitlement->visa [entitlement]
  (sign-visa (entitlement->visa-claims entitlement)))

(defn entitlements->passport [entitlements]
  {:ga4gh_passport_v1 (mapv entitlement->visa entitlements)})

;; Reading visas

(defn visa->researcher-status-by
  "Return the :by attribute of a decoded GA4GH Visa
  Claim, if the Visa asserts the \"Bona Fide\" researcher status."
  [visa-claim]
  ;; Let's keep this validation non-fatal for now.
  (log/info "Checking visa" (pr-str visa-claim))
  (when-let [errors (s/check VisaClaim visa-claim)]
    (log/warn "Visa didn't match our schema:" (pr-str errors)))
  ;; TODO should we check that "sub" of visa-claim matches the user?
  (when-let [visa (:ga4gh_visa_v1 visa-claim)]
    (when (and (= (:type visa) "ResearcherStatus")
               (#{"so" "system"} (:by visa))
               ;; should we also check this?
               #_(= (:value visa) "https://doi.org/10.1038/s41431-018-0219-y"))
      (getx visa :by))))

(defn trusted-issuers []
  (set (:ga4gh-visa-trusted-issuers env)))

(defn issuer-whitelisted? [iss jku]
  (contains? (trusted-issuers) {:iss iss :jku jku}))

(defn passport->researcher-status-by
  "Given an OIDC id token, check the visas in the :ga4gh_passport_v1
  claim. If any of the visas assert \"Bona Fide\" researcher status,
  return the :by attribute of the claim, as a keyword. If multiple
  research status visas are found, uses the first one."
  [id-token]
  (when-let [visas (:ga4gh_passport_v1 id-token)]
    (some identity
          (doall (for [visa visas]
                   (let [opened-visa (apply merge (jwt/show visa))
                         iss (:iss opened-visa)
                         jku (:jku opened-visa)]
                     (when (:log-authentication-details env)
                       (log/debug "Checking visa " (pr-str visa) opened-visa)
                       (log/debug "Checking issuer whitelist " {:iss iss :jku jku}))
                     (if (issuer-whitelisted? iss jku)
                       (do (when (:log-authentication-details env)
                             (log/debug "Validating visa" (pr-str visa) opened-visa))
                           (try (when-let [by (visa->researcher-status-by (jwt/validate-visa visa (Instant/now)))]
                                  {:researcher-status-by by})
                                (catch Throwable t
                                  (log/warn "Invalid visa" t))))
                       (do (log/warn ":ga4gh-visa-trusted-issuers does not contain " {:iss iss :jku jku})
                           nil))))))))

(defn visa->claims
  "Show the contents of the visa / JWT token without verifying it.

  Useful for testing or debugging."
  [visa]
  (jwt/show visa))
