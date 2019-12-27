(ns ^:integration rems.api.test-api
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-fixture)

(deftest test-api-not-found
  (testing "unknown endpoint"
    (let [resp (-> (request :get "/api/unknown")
                   handler)]
      (is (response-is-not-found? resp))))
  (testing "known endpoint, wrong method,"
    (testing "unauthorized"
      (let [resp (-> (request :get "/api/blacklist/remove")
                     handler)]
        (is (response-is-not-found? resp))))
    (testing "authorized,"
      (testing "missing params"
        ;; Surprisingly hard to find a POST route that isn't shadowed
        ;; by a GET route. For example, GET /api/applications/command
        ;; hits the /api/applications/:application-id route.
        (let [resp (-> (request :get "/api/blacklist/remove")
                       (authenticate "42" "handler")
                       handler)]
          (is (response-is-not-found? resp))))))
  (testing "API key roles"
    (testing "all available"
      (let [resp (-> (request :get "/api/forms")
                     (authenticate "42" "owner")
                     handler)]
        (is (= 200 (:status resp)))))
    (testing "handler and owner roles unavailable"
      (let [resp (-> (request :get "/api/forms")
                     (authenticate "43" "owner")
                     handler)]
        (is (response-is-forbidden? resp))))))
