(ns ^:integration rems.api.test-public
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          languages (keys data)]
      (is (= [:en :fi :sv] (sort languages))))))

(deftest test-config-api-smoke
  (let [config (-> (request :get "/api/config")
                   handler
                   read-ok-body)]
    (is (true? (:dev config)))))
