(ns ^:integration rems.api.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.test-data-functions :as test-data-functions]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all])
  (:import [java.util UUID]))

(use-fixtures
  :once
  api-fixture)

(deftest user-settings-api-test
  (let [user-id (str (UUID/randomUUID))]
    (test-data-functions/create-user! {:eppn user-id})

    (testing "default user settings"
      (is (= {:language "en"
              :notification-email nil}
             (-> (request :get "/api/user-settings")
                 (authenticate "42" user-id)
                 handler
                 read-ok-body))))

    (testing "update user settings"
      (-> (request :put "/api/user-settings")
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
