(ns ^:integration rems.api.test-email
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.email.core :as email]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest test-send-handler-reminder
  (let [api-key "42"
        called? (atom false)]
    (with-redefs [email/generate-handler-reminder-emails! (fn [] (reset! called? true))]
      (let [body (-> (request :post "/api/email/send-handler-reminder")
                     (authenticate api-key "developer")
                     handler
                     read-ok-body)]
        (is (= "OK" body))
        (is (true? @called?))))))
