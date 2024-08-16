(ns ^:integration rems.api.test-user-settings
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.api.testing :refer [api-fixture assert-response-is-ok authenticate read-body-and-status read-ok-body]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.users]
            [rems.service.test-data :as test-data :refer [+test-api-key+]]
            [rems.service.users]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer [json-body request]])
  (:import [java.util UUID]))

(use-fixtures
  :each
  api-fixture)

(deftest user-settings-api-test
  (test-data/create-test-api-key!)
  (let [user-id "pekka"]
    (test-helpers/create-user! {:userid user-id
                                :name "Pekka"
                                :email "pekka@example.com"})

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
                 (authenticate +test-api-key+ user-id)
                 handler
                 read-ok-body)))
      (is (= {:userid user-id
              :name "Pekka"
              :email "pekka@example.com"}
             (rems.service.users/get-user user-id))))

    (testing "update user settings"
      (-> (request :put "/api/user-settings/edit")
          (json-body {:language :fi
                      :notification-email "foo@example.com"})
          (authenticate +test-api-key+ user-id)
          handler
          assert-response-is-ok)
      (is (= {:language "fi"
              :notification-email "foo@example.com"}
             (-> (request :get "/api/user-settings")
                 (authenticate +test-api-key+ user-id)
                 handler
                 read-ok-body)))
      (is (= {:userid user-id
              :name "Pekka"
              :email "pekka@example.com"
              :notification-email "foo@example.com"}
             (rems.service.users/get-user user-id))))))
