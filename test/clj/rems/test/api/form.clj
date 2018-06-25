(ns ^:integration rems.test.api.form
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

(deftest form-api-filtering-test
  (let [unfiltered-data (-> (request :get "/api/form")
                            (authenticate "42" "owner")
                            app
                            read-body)
        filtered-data (-> (request :get "/api/form" {:active true})
                          (authenticate "42" "owner")
                          app
                          read-body)]
    (is (not (empty? unfiltered-data)))
    (is (not (empty? filtered-data)))
    (is (every? #(contains? % :active) unfiltered-data))
    (is (every? :active filtered-data))
    (is (< (count filtered-data) (count unfiltered-data)))))

(deftest form-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/form"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (testing "listing without owner role"
    (let [response (-> (request :get (str "/api/form"))
                       (authenticate "42" "alice")
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))
