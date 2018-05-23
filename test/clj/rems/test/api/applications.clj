(ns ^:integration rems.test.api.applications
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest applications-api-test
  (let [api-key "42"
        user-id "developer"]
    (let [data (-> (request :get "/api/applications")
                   (authenticate api-key user-id)
                   app
                   read-body)]
      (is (= [1 2 3 4 5 6 7] (map :id (sort-by :id data)))))))

(deftest applications-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/applications"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))
