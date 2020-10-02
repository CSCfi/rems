(ns rems.ga4gh
  "Implementation of GA4GH Passports and Visas
   <https://github.com/ga4gh-duri/ga4gh-duri.github.io/tree/master/researcher_ids>"
  (:require [buddy.core.keys :as keys]
            [clj-time.coerce]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.jwt :as jwt]
            [rems.testing-util :refer [with-fixed-time]]
            [schema.core :as s])
  (:import org.joda.time.DateTime))

;; TODO real keys
;; example keys from https://tools.ietf.org/html/rfc7517#appendix-A
(def +public-key+
  (json/parse-string
   "{\"kty\":\"RSA\",
     \"n\": \"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",
     \"e\":\"AQAB\",
     \"alg\":\"RS256\",
     \"kid\":\"2011-04-29\"}"))

(def +private-key+
  (json/parse-string
   "{\"kty\":\"RSA\",
     \"n\":\"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",
     \"e\":\"AQAB\",
     \"d\":\"X4cTteJY_gn4FYPsXB8rdXix5vwsg1FLN5E3EaG6RJoVH-HLLKD9M7dx5oo7GURknchnrRweUkC7hT5fJLM0WbFAKNLWY2vv7B6NqXSzUvxT0_YSfqijwp3RTzlBaCxWp4doFk5N2o8Gy_nHNKroADIkJ46pRUohsXywbReAdYaMwFs9tv8d_cPVY3i07a3t8MN6TNwm0dSawm9v47UiCl3Sk5ZiG7xojPLu4sbg1U2jx4IBTNBznbJSzFHK66jT8bgkuqsk0GjskDJk19Z4qwjwbsnn4j2WBii3RL-Us2lGVkY8fkFzme1z0HbIkfz0Y6mqnOYtqc0X4jfcKoAC8Q\",
     \"p\":\"83i-7IvMGXoMXCskv73TKr8637FiO7Z27zv8oj6pbWUQyLPQBQxtPVnwD20R-60eTDmD2ujnMt5PoqMrm8RfmNhVWDtjjMmCMjOpSXicFHj7XOuVIYQyqVWlWEh6dN36GVZYk93N8Bc9vY41xy8B9RzzOGVQzXvNEvn7O0nVbfs\",
     \"q\":\"3dfOR9cuYq-0S-mkFLzgItgMEfFzB2q3hWehMuG0oCuqnb3vobLyumqjVZQO1dIrdwgTnCdpYzBcOfW5r370AFXjiWft_NGEiovonizhKpo9VVS78TzFgxkIdrecRezsZ-1kYd_s1qDbxtkDEgfAITAG9LUnADun4vIcb6yelxk\",
     \"dp\":\"G4sPXkc6Ya9y8oJW9_ILj4xuppu0lzi_H7VTkS8xj5SdX3coE0oimYwxIi2emTAue0UOa5dpgFGyBJ4c8tQ2VF402XRugKDTP8akYhFo5tAA77Qe_NmtuYZc3C3m3I24G2GvR5sSDxUyAN2zq8Lfn9EUms6rY3Ob8YeiKkTiBj0\",
     \"dq\":\"s9lAH9fggBsoFR8Oac2R_E2gw282rT2kGOAhvIllETE1efrA6huUUvMfBcMpn8lqeW6vzznYY5SSQF7pMdC_agI3nG8Ibp1BUb0JUiraRNqUfLhcQb_d9GF4Dh7e74WbRsobRonujTYN1xCaP6TO61jvWrX-L18txXw494Q_cgk\",
     \"qi\":\"GyM_p6JrXySiz1toFgKbWV-JdI3jQ4ypu9rbMWx3rQJBfmt0FoYzgUIZEVFEcOqwemRN81zoDAaa-Bk0KWNGDjJHZDdDmFhW3AN7lI-puxk_mHZGJ11rxyR8O55XLSe3SPmRfKwZI6yU24ZxvQKFYItdldUKGzO6Ia6zTKhAVRU\",
     \"alg\":\"RS256\",
     \"kid\":\"2011-04-29\"}"))

(def +private-key-parsed+
  (keys/jwk->private-key +private-key+))

(def +public-key-parsed+
  (keys/jwk->public-key +public-key+))

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

(defn- visa-header []
  {:jku (str (:public-url env) "api/jwk")
   :typ "JWT"
   :kid (:kid +private-key+)})

(defn- sign-visa [visa]
  ;; TODO look up algorithm from key?
  (jwt/sign (s/validate VisaClaim visa) +private-key-parsed+ {:alg :rs256 :header (visa-header)}))

(def +default-length+ (time/years 1))

(defn- entitlement->visa-claims [{:keys [resid _catappid start end _mail userid _approvedby]}]
  {:iss (:public-url env)
   :sub userid
   :iat (clj-time.coerce/to-long (time/now))
   :exp (clj-time.coerce/to-long (or end (time/plus (time/now) +default-length+)))
   :scope "openid"
   :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                   :value (str resid)
                   :source (:public-url env)
                   :by "dac" ;; the Data Access Commitee acts via REMS
                   :asserted (clj-time.coerce/to-long start)}})

(deftest test-entitlement->visa-claims
  (with-redefs [env {:public-url "https://rems.example/"}]
    (with-fixed-time (time/date-time 2010 01 01)
      (fn []
        (is (= {:iss "https://rems.example/"
                :sub "user@example.com"
                :iat (clj-time.coerce/to-long "2010")
                :exp (clj-time.coerce/to-long "2011")
                :scope "openid"
                :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                                :value "urn:1234"
                                :source "https://rems.example/"
                                :by "dac"
                                :asserted (clj-time.coerce/to-long "2009")}}
               (entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :userid "user@example.com"})))
        (is (= {:iss "https://rems.example/"
                :sub "user@example.com"
                :iat (clj-time.coerce/to-long "2010")
                :exp (clj-time.coerce/to-long "2010-06-02")
                :scope "openid"
                :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                                :value "urn:1234"
                                :source "https://rems.example/"
                                :by "dac"
                                :asserted (clj-time.coerce/to-long "2009")}}
               (entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :end (time/date-time 2010 6 2) :userid "user@example.com"})))))))

(defn- entitlement->visa [entitlement]
  (sign-visa (entitlement->visa-claims entitlement)))

(defn entitlements->passport [entitlements]
  {:ga4gh_passport_v1 (mapv entitlement->visa entitlements)})
