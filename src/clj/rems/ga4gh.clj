(ns rems.ga4gh
  "Implementation of GA4GH Passports and Visas
   <https://github.com/ga4gh-duri/ga4gh-duri.github.io/tree/master/researcher_ids>"
  (:require [rems.jwt :as jwt])
  (:import org.joda.time.DateTime))

(defn- entitlement->grant [{:keys [resid _catappid start _end _mail _userid approvedby]}]
  (let [start-datetime (DateTime. start)]
    ;; TODO should this send the end time also if we know it?
    (jwt/sign {:type "ControlledAccessGrants"
               :value (str resid)
               :source "https://ga4gh.org/duri/no_org"
               :by (str approvedby)
               :asserted (.getMillis start-datetime)}
              "secret"))) ;;TODO use key/real secret here

(defn entitlements->visa [entitlements]
  {:ga4gh_visa_v1 (mapv entitlement->grant entitlements)})
