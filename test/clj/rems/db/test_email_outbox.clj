(ns ^:integration rems.db.test-email-outbox
  (:require [clojure.test :refer :all]
            [rems.db.email-outbox :as email-outbox]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]])
  (:import [org.joda.time DateTime]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-email-outbox
  (let [id1 (email-outbox/put! {:email {:to-user "user 1"
                                        :subject "subject 1"
                                        :body "body 1"}
                                :attempts 5})
        id2 (email-outbox/put! {:email {:to "user2@example.com"
                                        :subject "subject 2"
                                        :body "body 2"}
                                :attempts 5})]
    (testing "put"
      (is (number? id1))
      (is (number? id2)))

    (testing "get by id"
      (let [emails (email-outbox/get-emails {:ids [id1]})
            email (first emails)]
        (is (= 1 (count emails)))
        (is (= {:email-outbox/id id1
                :email-outbox/email {:to-user "user 1"
                                     :subject "subject 1"
                                     :body "body 1"}
                :email-outbox/latest-attempt nil
                :email-outbox/latest-error ""
                :email-outbox/remaining-attempts 5}
               (dissoc email :email-outbox/created)))
        (is (instance? DateTime (:email-outbox/created email)))))

    (testing "get all"
      (is (= [{:email-outbox/id id1
               :email-outbox/email {:to-user "user 1"
                                    :subject "subject 1"
                                    :body "body 1"}}
              {:email-outbox/id id2
               :email-outbox/email {:to "user2@example.com"
                                    :subject "subject 2"
                                    :body "body 2"}}]
             (->> (email-outbox/get-emails)
                  (map #(select-keys % [:email-outbox/id :email-outbox/email]))
                  (sort-by :email-outbox/id)))))))
