(ns ^:integration rems.test.api.resources
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest resources-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      ;; just a basic smoke test for now
      (let [response (-> (request :get "/api/resources")
                         (authenticate api-key user-id)
                         app)
            data (read-body response)]
        (is (response-is-ok? response))
        (is (coll-is-not-empty? data))
        (is (= #{:id :owneruserid :modifieruserid :prefix :resid :start :end :active :licenses} (set (keys (first data)))))))
    (testing "create"
      (let [licid 1
            resid "RESOURCE-API-TEST"]
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid resid
                                       :prefix "TEST-PREFIX"
                                       :licenses [licid]})
                           app)]
          (is (response-is-ok? response)))
        (testing "and fetch"
          (let [response (-> (request :get "/api/resources")
                             (authenticate api-key user-id)
                             app)
                data (read-body response)
                resource (some #(when (= resid (:resid %)) %) data)]
            (is (response-is-ok? response))
            (is resource)
            (is (= [licid] (map :id (:licenses resource))))))))))

(deftest resources-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/resources")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/resources" {:active true})
                              (authenticate "42" "owner")
                              app)
        filtered-data (read-body filtered-response)]
    (is (response-is-ok? unfiltered-response))
    (is (response-is-ok? filtered-response))
    (is (coll-is-not-empty? unfiltered-data))
    (is (coll-is-not-empty? filtered-data))
    (is (every? #(contains? % :active) unfiltered-data))
    (is (every? :active filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

(deftest resources-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get "/api/resources")
                         app)]
        (is (= 401 (:status response)))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post "/api/resources/create")
                         (json-body {:resid "r"
                                     :prefix "p"
                                     :licenses []})
                         app)]
        (is (= 403 (:status response)))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "with wrong api key"
    (let [api-key "1"
          user-id "owner"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           app)]
          (is (= 401 (:status response)))
          (is (= "invalid api key" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :prefix "p"
                                       :licenses []})
                           app)]
          (is (= 401 (:status response)))
          (is (= "invalid api key" (read-body response)))))))

  (testing "without owner role"
    (let [api-key "42"
          user-id "alice"]
      (testing "list"
        (let [response (-> (request :get "/api/resources")
                           (authenticate api-key user-id)
                           app)]
          (is (= 401 (:status response)))
          (is (= "unauthorized" (read-body response)))))
      (testing "create"
        (let [response (-> (request :post "/api/resources/create")
                           (authenticate api-key user-id)
                           (json-body {:resid "r"
                                       :prefix "p"
                                       :licenses []})
                           app)]
          (is (= 401 (:status response)))
          (is (= "unauthorized" (read-body response))))))))
