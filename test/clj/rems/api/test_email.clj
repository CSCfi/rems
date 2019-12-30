(ns ^:integration rems.api.test-email
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.outbox :as outbox]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest test-send-handler-reminder
  (let [api-key "42"
        outbox-emails (atom [])]
    (with-redefs [outbox/put! (fn [email]
                                (swap! outbox-emails conj email))]
      (let [body (-> (request :post "/api/email/send-handler-reminder")
                     (authenticate api-key "developer")
                     handler
                     read-ok-body)]
        (is (= "OK" body))
        (is (= [{:subject "Applications in progress", :to-user "developer"}
                {:subject "Applications in progress", :to-user "handler"}]
               (->> @outbox-emails
                    (map #(select-keys (:outbox/email %) [:subject :to-user]))
                    (sort-by :to-user))))))))
