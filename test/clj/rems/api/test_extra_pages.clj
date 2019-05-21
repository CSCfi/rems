(ns ^:integration rems.api.test-extra-pages
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest extra-pages-api-test
  (let [api-key "42"
        user-id "owner"]
    (testing "fetch existing extra-page"
      (let [response (api-call :get "/api/extra-pages/test" {} api-key user-id)]
        (is (= (set (keys response)) #{:en :fi}))
        (is (= (:en response) "This is a test.\n"))))
    (testing "try to fetch non-existing extra-page"
      (let [response (-> (request :get "/api/extra-pages/test2")
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-not-found? response))))))
