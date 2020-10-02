(ns ^:integration rems.api.test-permissions
  (:require [buddy.sign.jws :as buddy-jws]
            [buddy.sign.jwt :as buddy-jwt]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.config]
            [rems.ga4gh :as ga4gh]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]
            [schema.core :as s]))

(use-fixtures
  :once
  api-fixture
  (fn [f]
    (with-redefs [rems.config/env (assoc rems.config/env :enable-permissions-api true)]
      (f))))

(deftest jwk-api
  (let [data (api-call :get "/api/jwk" nil nil nil)]
    (is (= {:keys [{:alg "RS256"
                    :n "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
                    :kid "2011-04-29"
                    :e "AQAB"
                    :kty "RSA"}]}
           data))))

(defn- validate-visa [visa]
  (s/validate ga4gh/VisaClaim visa))

(defn- validate-alice-result [data]
  (doseq [visa (:ga4gh_passport_v1 data)]
    (let [header (buddy-jws/decode-header visa)
          data (buddy-jwt/unsign visa ga4gh/+public-key-parsed+ {:alg :rs256})]
      (is (= (str (:public-url rems.config/env) "api/jwk") (:jku header)))
      (is (= "JWT" (:typ header)))
      (is (= "2011-04-29" (:kid header)))
      (validate-visa data)
      (is (= "alice" (:sub data)))
      (is (= "urn:nbn:fi:lb-201403262" (get-in data [:ga4gh_visa_v1 :value]))))))

(deftest permissions-test-content
  (let [api-key "42"]
    (testing "all for alice as handler"
      (let [data (-> (request :get "/api/permissions/alice")
                     (authenticate api-key "handler")
                     handler
                     read-ok-body)]
        (validate-alice-result data)))

    (testing "all for alice as owner"
      (let [data (-> (request :get "/api/permissions/alice")
                     (authenticate api-key "owner")
                     handler
                     read-ok-body)]
        (validate-alice-result data)))

    (testing "without user not found is returned"
      (let [response (-> (request :get "/api/permissions")
                         (authenticate api-key "handler")
                         handler)
            body (read-body response)]
        (is (= "not found" body))))))

(deftest permissions-test-security
  (let [api-key "42"]
    (testing "listing without authentication"
      (let [response (-> (request :get (str "/api/permissions/userx"))
                         handler)
            body (read-body response)]
        (is (= "unauthorized" body))))

    (testing "listing without appropriate role"
      (let [response (-> (request :get (str "/api/permissions/alice"))
                         (authenticate api-key "approver1")
                         handler)
            body (read-body response)]
        (is (= "forbidden" body))))

    (testing "all for alice as malice"
      (let [response (-> (request :get (str "/api/permissions/alice"))
                         (authenticate api-key "malice")
                         handler)
            body (read-body response)]
        (is (= "forbidden" body))))))

(deftest permissions-test-api-disabled
  (with-redefs [rems.config/env (assoc rems.config/env :enable-permissions-api false)]
    (let [api-key "42"]
      (testing "when permissions api is disabled"
        (let [response (-> (request :get "/api/permissions/alice")
                           (authenticate api-key "handler")
                           handler)
              body (read-body response)]
          (is (= "permissions api not implemented" body)))))))
