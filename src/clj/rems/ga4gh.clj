(ns rems.ga4gh
  "Implementation of GA4GH Passports and Visas
   <https://github.com/ga4gh-duri/ga4gh-duri.github.io/tree/master/researcher_ids>"
  (:require [clj-time.coerce]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.jwt :as jwt]
            [rems.testing-util :refer [with-fixed-time]]
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

(def +default-length+ (time/years 1))

(def +issuer+ "TODO")

(defn- entitlement->visa-claims [{:keys [resid _catappid start end _mail userid _approvedby]}]
  {:iss +issuer+
   :sub userid
   :iat (clj-time.coerce/to-long (time/now))
   :exp (clj-time.coerce/to-long (or end (time/plus (time/now) +default-length+)))
   :scope "openid"
   :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                   :value (str resid)
                   :source "https://no.organization" ;; TODO
                   :by "system" ;; TODO or "so"?
                   :asserted (clj-time.coerce/to-long start)}})

(deftest test-entitlement->visa-claims
  (with-fixed-time (time/date-time 2010 01 01)
    (fn []
      (is (= {:iss "TODO"
              :sub "user@example.com"
              :iat (clj-time.coerce/to-long "2010")
              :exp (clj-time.coerce/to-long "2011")
              :scope "openid"
              :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                              :value "urn:1234"
                              :source "https://no.organization"
                              :by "system"
                              :asserted (clj-time.coerce/to-long "2009")}}
             (entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :userid "user@example.com"})))
      (is (= {:iss "TODO"
              :sub "user@example.com"
              :iat (clj-time.coerce/to-long "2010")
              :exp (clj-time.coerce/to-long "2010-06-02")
              :scope "openid"
              :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                              :value "urn:1234"
                              :source "https://no.organization"
                              :by "system"
                              :asserted (clj-time.coerce/to-long "2009")}}
             (entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :end (time/date-time 2010 6 2) :userid "user@example.com"}))))))

(defn- entitlement->visa [entitlement]
  (sign-visa (entitlement->visa-claims entitlement)))

(defn entitlements->passport [entitlements]
  {:ga4gh_passport_v1 (mapv entitlement->visa entitlements)})
