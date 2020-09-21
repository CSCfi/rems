(ns rems.ga4gh
  "Implementation of GA4GH Passports and Visas
   <https://github.com/ga4gh-duri/ga4gh-duri.github.io/tree/master/researcher_ids>"
  (:require [rems.jwt :as jwt]
            [schema.core :as s])
  (:import org.joda.time.DateTime))

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
   :scope (s/eq "openid") ;; could also list multiple space-separated scopes, as long as one is "openid"
   (s/optional-key :jti) s/Str
   :ga4gh_visa_v1 VisaObject})

(defn- sign-visa [visa]
  (jwt/sign (s/validate VisaClaim visa) "secret")) ;; TODO use key/real secret here

(defn- entitlement->visa [{:keys [resid _catappid start _end _mail _userid _approvedby]}]
  (let [start-datetime (DateTime. start)]
    ;; TODO should this send the end time also if we know it?
    (sign-visa {:iss "TODO"
                :sub "TODO"
                :iat 0
                :exp 0
                :scope "openid"
                :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                                :value (str resid)
                                :source "https://no.organization" ;; TODO
                                :by "system" ;; TODO or "so"?
                                :asserted (.getMillis start-datetime)}})))

(defn entitlements->passport [entitlements]
  {:ga4gh_passport_v1 (mapv entitlement->visa entitlements)})
