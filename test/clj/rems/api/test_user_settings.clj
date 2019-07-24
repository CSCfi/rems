(ns ^:integration rems.api.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(def user-id "david")

(def new-user
  {:eppn user-id
   :mail "d@av.id"
   :commonName "David Newuser"})

(def new-settings
  {:user-id user-id
   :language :en})

(use-fixtures
  :once
  api-fixture)

(deftest user-settings-api-test
  (is (= {} (user-settings/get-user-settings user-id)))
  (-> (request :post "/api/users/create")
      (json-body new-user)
      (authenticate "42" "owner")
      handler
      assert-response-is-ok)
  (testing "update user settings"
    (-> (request :put "/api/user-settings/update")
        (json-body new-settings)
        (authenticate "42" user-id)
        handler
        assert-response-is-ok)
    (is (= {:language :en}
           (user-settings/get-user-settings user-id))))
  (testing "get user settings"
    (let [body (-> (request :get "/api/user-settings")
                   (authenticate "42" user-id)
                   handler
                   read-ok-body)]
      (is (= body {:language "en"})))))
