(ns ^:integration rems.test.api.catalogue
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest catalogue-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))
