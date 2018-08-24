(ns ^:integration rems.test.api.form-items
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.db.core :as db]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [index-by]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest form-items-api-filtering-test
  (let [unfiltered-response (-> (request :get "/api/form-items")
                                (authenticate "42" "owner")
                                app)
        unfiltered-data (read-body unfiltered-response)
        filtered-response (-> (request :get "/api/form-items" {:active true})
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

(deftest form-items-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/form-items"))
                       app)
          body (read-body response)]
      (is (= 401 (:status response)))
      (is (= "unauthorized" body))))
  (testing "listing without owner role"
    (let [response (-> (request :get (str "/api/form-items"))
                       (authenticate "42" "alice")
                       app)
          body (read-body response)]
      (is (= 401 (:status response)))
      (is (= "unauthorized" body)))))
