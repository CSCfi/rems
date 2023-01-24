(ns ^:integration rems.api.test-permissions
  (:require [buddy.sign.jws :as buddy-jws]
            [buddy.sign.jwt :as buddy-jwt]
            [buddy.core.keys :as buddy-keys]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.config]
            [rems.ga4gh :as ga4gh]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]
            [schema.core :as s]))

(use-fixtures
  :once
  api-fixture)

(deftest jwk-api
  (let [data (api-call :get "/api/jwk" nil nil nil)]
    (is (= {:keys [{:alg "RS256"
                    :n "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
                    :kid "2011-04-29"
                    :e "AQAB"
                    :kty "RSA"}]}
           data)))
  (testing ":enable-permissions-api false"
    (with-redefs [rems.config/env (assoc rems.config/env :enable-permissions-api false)]
      (let [response (api-response :get "/api/jwk")]
        (is (response-is-not-implemented? response))
        (is (empty? (:body response))))))
  (testing ":ga4gh-visa-public-key not configured"
    (with-redefs [rems.config/env (dissoc rems.config/env :ga4gh-visa-public-key)]
      (let [response (api-response :get "/api/jwk")]
        (is (response-is-server-error? response))
        (is (empty? (:body response)))))))

(defn- validate-visa [visa]
  (s/validate ga4gh/VisaClaim visa))

(defn- validate-alice-result [data]
  (let [visas (:ga4gh_passport_v1 data)
        visa (first visas)
        header (buddy-jws/decode-header visa)
        key (buddy-keys/jwk->public-key (rems.config/env :ga4gh-visa-public-key))
        data (buddy-jwt/unsign visa key {:alg :rs256})]
    (is (= 1 (count visas)))
    (is visa)
    (is (= (str (:public-url rems.config/env) "api/jwk") (:jku header)))
    (is (= "JWT" (:typ header)))
    (is (= "2011-04-29" (:kid header)))
    (validate-visa data)
    (is (= "alice" (:sub data)))
    (is (= "urn:nbn:fi:lb-201403262" (get-in data [:ga4gh_visa_v1 :value])))))

(deftest permissions-test-content
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (let [res-id (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"})
        wf-id (test-helpers/create-workflow! {:handlers ["handler"]})
        cat-id (test-helpers/create-catalogue-item! {:resource-id res-id :workflow-id wf-id})
        application (test-helpers/create-application! {:catalogue-item-ids [cat-id] :actor "alice"})]
    (test-helpers/submit-application {:application-id application
                                      :actor "alice"})
    (test-helpers/command! {:type :application.command/approve
                            :application-id application
                            :actor "handler"}))

  (testing ":enable-permissions-api false"
    (with-redefs [rems.config/env (assoc rems.config/env :enable-permissions-api false)]
      (is (-> (request :get "/api/permissions/alice")
              (authenticate test-data/+test-api-key+ "owner")
              handler
              (response-is-not-implemented?)))))

  (testing "all for alice as alice"
    (let [data (-> (request :get "/api/permissions/alice")
                   (authenticate test-data/+test-api-key+ "alice")
                   handler
                   read-ok-body)]
      (validate-alice-result data)))

  (testing "all for alice as handler"
    (let [data (-> (request :get "/api/permissions/alice")
                   (authenticate test-data/+test-api-key+ "handler")
                   handler
                   read-ok-body)]
      (validate-alice-result data)))

  (testing "all for alice as owner"
    (let [data (-> (request :get "/api/permissions/alice")
                   (authenticate test-data/+test-api-key+ "owner")
                   handler
                   read-ok-body)]
      (validate-alice-result data)))

  (testing "without user not found is returned"
    (let [response (-> (request :get "/api/permissions")
                       (authenticate test-data/+test-api-key+ "handler")
                       handler)
          body (read-body response)]
      (is (= "not found" body)))))

(deftest permissions-test-security
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/permissions/userx"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body))))

  (testing "listing without appropriate role"
    (let [response (-> (request :get (str "/api/permissions/alice"))
                       (authenticate test-data/+test-api-key+ "approver1")
                       handler)
          body (read-body response)]
      (is (= "forbidden" body))))

  (testing "all for alice as malice"
    (let [response (-> (request :get (str "/api/permissions/alice"))
                       (authenticate test-data/+test-api-key+ "malice")
                       handler)
          body (read-body response)]
      (is (= "forbidden" body)))))

