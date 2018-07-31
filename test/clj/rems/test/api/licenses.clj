(ns ^:integration rems.test.api.licenses
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest licenses-api-test
  (testing "get"
    (let [response (-> (request :get "/api/licenses")
                       (authenticate "42" "owner")
                       app)
          data (read-body response)]
      (is (response-is-ok? response))
      (is (coll-is-not-empty? data))
      (is (= #{:id :start :end :licensetype :title :textcontent :localizations} (set (keys (first data))))))))

(deftest licenses-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/licenses")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/licenses" {:active true})
                              (authenticate "42" "owner")
                              app)
        filtered-data (read-body filtered-response)]
    (is (response-is-ok? unfiltered-response))
    (is (response-is-ok? filtered-response))
    (is (coll-is-not-empty? unfiltered-data))
    (is (coll-is-not-empty? filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

(deftest licenses-api-security-test
  (testing "without authentication"
    (let [response (-> (request :get "/api/licenses")
                       app)]
      (is (= 401 (:status response)))))
  (testing "without owner role"
    (let [response (-> (request :get "/api/licenses")
                       (authenticate "42" "alice")
                       app)]
      (is (= 401 (:status response)))))
  (testing "with owner role"
    (let [body (-> (request :get "/api/licenses")
                   (authenticate "42" "owner")
                   app
                   read-body)]
      (is (string? (:licensetype (first body)))))))
