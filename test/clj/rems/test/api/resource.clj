(ns ^:integration rems.test.api.resource
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

(deftest resource-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "get"
      ;; just a basic smoke test for now
      (let [response (-> (request :get "/api/resource")
                         (authenticate api-key user-id)
                         app)
            data (read-body response)]
        (is (= 200 (:status response)))
        (is (seq? data))
        (is (not (empty? data)))
        (is (= #{:id :modifieruserid :prefix :resid :start :end :licenses} (set (keys (first data)))))))
    (testing "create"
      (let [licid 1
            resid "RESOURCE-API-TEST"]
        (let [response (-> (request :put "/api/resource/create")
                           (authenticate api-key user-id)
                           (json-body {:resid resid
                                       :prefix "TEST-PREFIX"
                                       :licenses [licid]})
                           app)]
          (is (= 200 (:status response))))
        (testing "and fetch"
          (let [response (-> (request :get "/api/resource")
                             (authenticate api-key user-id)
                             app)
                data (read-body response)
                resource (some #(when (= resid (:resid %)) %) data)]
            (is (= 200 (:status response)))
            (is resource)
            (is (= [licid] (map :id (:licenses resource))))))))))

(deftest resource-api-security-test
  (testing "without authentication"
    (let [response (-> (request :get "/api/resource")
                       app)]
      (is (= 401 (:status response))))
    (let [response (-> (request :put "/api/resource/create")
                       (json-body {:resid "r"
                                   :prefix "p"
                                   :licenses []})
                       app)]
      (is (.contains (:body response) "Invalid anti-forgery token"))))
  (testing "without owner role"
    (let [api-key "42"
          user-id "alice"]
      (let [response (-> (request :get "/api/resource")
                         (authenticate api-key user-id)
                         app)]
        (is (= 401 (:status response))))
      (let [response (-> (request :put "/api/resource/create")
                         (authenticate api-key user-id)
                         (json-body {:resid "r"
                                     :prefix "p"
                                     :licenses []})
                         app)]
        (is (= 401 (:status response)))))))
