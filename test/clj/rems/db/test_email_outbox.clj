(ns ^:integration rems.db.test-email-outbox
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.email-outbox :as email-outbox]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]])
  (:import [org.joda.time DateTime Duration DateTimeUtils]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-email-outbox
  (let [deadline (-> (time/now) (.plusMinutes 1))
        id1 (email-outbox/put! {:email {:to-user "user 1"
                                        :subject "subject 1"
                                        :body "body 1"}
                                :deadline deadline})
        id2 (email-outbox/put! {:email {:to "user2@example.com"
                                        :subject "subject 2"
                                        :body "body 2"}
                                :deadline deadline})]
    (testing "put"
      (is (number? id1))
      (is (number? id2))
      (is (not= id1 id2)))

    (testing "get by id"
      (let [emails (email-outbox/get-emails {:ids [id1]})
            email (email-outbox/get-email-by-id id1)]
        (is (= 1 (count emails)))
        (is (= emails [email]))
        (is (= {:email-outbox/id id1
                :email-outbox/email {:to-user "user 1"
                                     :subject "subject 1"
                                     :body "body 1"}
                :email-outbox/latest-attempt nil
                :email-outbox/latest-error ""
                :email-outbox/backoff (Duration. 10000)
                :email-outbox/deadline deadline}
               (dissoc email :email-outbox/created :email-outbox/next-attempt)))
        (is (instance? DateTime (:email-outbox/created email)))
        (is (instance? DateTime (:email-outbox/next-attempt email)))))

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
                  (sort-by :email-outbox/id)))))

    (testing "get due now, before attempt"
      (is (= [id1 id2]
             (->> (email-outbox/get-emails {:due-now? true})
                  (map :email-outbox/id)
                  sort))))

    (testing "attempt failed"
      (let [email (email-outbox/get-email-by-id id1)]
        (email-outbox/attempt-failed! email "the error message"))
      (let [email (email-outbox/get-email-by-id id1)
            unrelated (email-outbox/get-email-by-id id2)]
        (is (= {:email-outbox/id id1
                :email-outbox/latest-error "the error message"
                :email-outbox/backoff (Duration. 20000)}
               (select-keys email [:email-outbox/id
                                   :email-outbox/latest-error
                                   :email-outbox/backoff])))
        (is (instance? DateTime (:email-outbox/next-attempt email)))
        (is (instance? DateTime (:email-outbox/latest-attempt email)))
        (is (nil? (:email-outbox/latest-attempt unrelated)))))

    (testing "get due now, after attempt"
      (is (= [id2] ; email id1 should be scheduled in future
             (->> (email-outbox/get-emails {:due-now? true})
                  (map :email-outbox/id)
                  sort))))

    (testing "all attempts failed"
      (let [email (email-outbox/get-email-by-id id1)]
        (try
          (DateTimeUtils/setCurrentMillisFixed (.getMillis (:email-outbox/deadline email)))
          ;; failed attempt after the deadline
          (email-outbox/attempt-failed! email "the error message")
          (finally
            (DateTimeUtils/setCurrentMillisSystem))))
      (let [email (email-outbox/get-email-by-id id1)]
        (is email)
        (is (nil? (:email-outbox/next-attempt email)))))

    (testing "attempt succeeded"
      (email-outbox/attempt-succeeded! id1)
      (is (= [id2]
             (->> (email-outbox/get-emails)
                  (map :email-outbox/id)
                  sort))
          "successfully sent emails should be removed from the outbox"))))
