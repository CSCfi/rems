(ns ^:integration rems.api.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import [java.util UUID]))

(use-fixtures
  :each
  api-fixture)

(deftest user-settings-api-test
  (test-data/create-test-api-key!)
  (let [user-id (str (UUID/randomUUID))]
    (test-helpers/create-user! {:userid user-id})

    (testing "default user settings without authentification"
      (is (= {:status 401
              :body "unauthorized"}
             (-> (request :get "/api/user-settings")
                 handler
                 read-body-and-status))))

    (testing "default user settings"
      (is (= {:language "en"
              :notification-email nil}
             (-> (request :get "/api/user-settings")
                 (authenticate "42" user-id)
                 handler
                 read-ok-body))))

    (testing "update user settings"
      (-> (request :put "/api/user-settings/edit")
          (json-body {:language :fi})
          (authenticate "42" user-id)
          handler
          assert-response-is-ok)
      (is (= {:language "fi"
              :notification-email nil}
             (-> (request :get "/api/user-settings")
                 (authenticate "42" user-id)
                 handler
                 read-ok-body))))))
