(ns ^:integration rems.api.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(def user-id "david")

(def new-user
  {:userid user-id
   :email "d@av.id"
   :name "David Newuser"})

(def new-settings
  {:language :fi})

(use-fixtures
  :once
  api-fixture)

(deftest user-settings-api-test
  (-> (request :post "/api/users/create")
      (json-body new-user)
      (authenticate "42" "owner")
      handler
      assert-response-is-ok)
  (testing "update user settings"
    (-> (request :put "/api/user-settings")
        (json-body new-settings)
        (authenticate "42" user-id)
        handler
        assert-response-is-ok))
  (testing "get user settings"
    (let [body (-> (request :get "/api/user-settings")
                   (authenticate "42" user-id)
                   handler
                   read-ok-body)]
      (is (= {:language "fi"
              :email nil}
             body)))))
