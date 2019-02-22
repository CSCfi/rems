(ns ^:integration rems.test.api.resources
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest resources-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      ;; just a basic smoke test for now
      (let [data (-> (request :get "/api/resources")
                     (authenticate api-key user-id)
                     app
                     assert-response-is-ok
                     read-body)]
        (is (coll-is-not-empty? data))
        (is (:id (set (keys (first data)))))))
    (testing "create"
      (let [licid 1
            resid "RESOURCE-API-TEST"]
        (-> (request :post "/api/resources/create")
            (authenticate api-key user-id)
            (json-body {:resid resid
                        :organization "TEST-ORGANIZATION"
                        :licenses [licid]})
            app
            assert-response-is-ok)
        (testing "and fetch"
          (let [data (-> (request :get "/api/resources")
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok
                         read-body)
                resource (some #(when (= resid (:resid %)) %) data)]
            (is resource)
            (is (= [licid] (map :id (:licenses resource))))))
        (testing "duplicate resource ID is not allowed within one organization"
          (let [response (-> (request :post "/api/resources/create")
                             (authenticate api-key user-id)
                             (json-body {:resid resid
                                         :organization "TEST-ORGANIZATION"
                                         :licenses [licid]})
                             app)
                body (read-body response)]
            (is (= 200 (:status response)))
            (is (= false (:success body)))
            (is (= [{:type "t.administration.errors/duplicate-resid" :resid resid}] (:errors body)))))
        (testing "duplicate resource ID is allowed between organizations"
          (-> (request :post "/api/resources/create")
              (authenticate api-key user-id)
              (json-body {:resid resid
                          :organization "TEST-ORGANIZATION2"
                          :licenses [licid]})
              app
              assert-response-is-ok))))))

(deftest resources-api-filtering-test
  (let [unfiltered (-> (request :get "/api/resources")
                       (authenticate "42" "owner")
                       app
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/resources" {:active true})
                     (authenticate "42" "owner")
                     app
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :active) unfiltered))
    (is (every? :active filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest resources-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get "/api/resources")
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/resources/create")
                         (json-body {:resid "r"
                                     :organization "o"
                                     :licenses []})
                         app)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "with wrong api key"
    (let [api-key "1"
          user-id "owner"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           app)]
          (is (response-is-unauthorized? response))
          (is (= "invalid api key" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization "o"
                                       :licenses []})
                           app)]
          (is (response-is-unauthorized? response))
          (is (= "invalid api key" (read-body response)))))))

  (testing "without owner role"
    (let [api-key "42"
          user-id "alice"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           app)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :organization "o"
                                       :licenses []})
                           app)]
          (is (response-is-forbidden? response))
          (is (= "forbidden" (read-body response))))))))
