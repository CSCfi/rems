(ns rems.test-ga4gh
  (:require [clj-time.coerce]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.config :refer [env]]
            [rems.ga4gh :as ga4gh]
            [rems.testing-util :refer [with-fixed-time]]))

(deftest test-entitlement->visa-claims
  (with-redefs [env {:public-url "https://rems.example/"}]
    (with-fixed-time (time/date-time 2010 01 01)
      (fn []
        (is (= {:iss "https://rems.example/"
                :sub "user@example.com"
                :iat (clj-time.coerce/to-epoch "2010")
                :exp (clj-time.coerce/to-epoch "2011")
                :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                                :value "urn:1234"
                                :source "https://rems.example/"
                                :by "dac"
                                :asserted (clj-time.coerce/to-epoch "2009")}}
               (#'ga4gh/entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :userid "user@example.com"})))
        (is (= {:iss "https://rems.example/"
                :sub "user@example.com"
                :iat (clj-time.coerce/to-epoch "2010")
                :exp (clj-time.coerce/to-epoch "2010-06-02")
                :ga4gh_visa_v1 {:type "ControlledAccessGrants"
                                :value "urn:1234"
                                :source "https://rems.example/"
                                :by "dac"
                                :asserted (clj-time.coerce/to-epoch "2009")}}
               (#'ga4gh/entitlement->visa-claims {:resid "urn:1234" :start (time/date-time 2009) :end (time/date-time 2010 6 2) :userid "user@example.com"})))))))
