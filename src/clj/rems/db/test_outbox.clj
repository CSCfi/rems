(ns ^:integration rems.db.test-outbox
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.outbox :as outbox])
  (:import [org.joda.time DateTime Duration DateTimeUtils]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-outbox
  (let [deadline (-> (time/now) (.plusMinutes 1))
        id1 (outbox/put! {:outbox/type :email
                          :outbox/email {:to-user "user 1"
                                         :subject "subject 1"
                                         :body "body 1"}
                          :outbox/deadline deadline})
        id2 (outbox/put! {:outbox/type :email
                          :outbox/email {:to "user2@example.com"
                                         :subject "subject 2"
                                         :body "body 2"}
                          :outbox/deadline deadline})]
    (testing "put"
      (is (number? id1))
      (is (number? id2))
      (is (not= id1 id2)))

    (testing "get by id"
      (let [emails (outbox/get-entries {:ids [id1]})
            email (outbox/get-entry-by-id id1)]
        (is (= 1 (count emails)))
        (is (= emails [email]))
        (is (= {:outbox/type :email
                :outbox/id id1
                :outbox/email {:to-user "user 1"
                               :subject "subject 1"
                               :body "body 1"}
                :outbox/latest-attempt nil
                :outbox/latest-error nil
                :outbox/backoff (Duration. 10000)
                :outbox/deadline deadline}
               (dissoc email :outbox/created :outbox/next-attempt)))
        (is (instance? DateTime (:outbox/created email)))
        (is (instance? DateTime (:outbox/next-attempt email)))))

    (testing "get all"
      (is (= [{:outbox/id id1
               :outbox/email {:to-user "user 1"
                              :subject "subject 1"
                              :body "body 1"}}
              {:outbox/id id2
               :outbox/email {:to "user2@example.com"
                              :subject "subject 2"
                              :body "body 2"}}]
             (->> (outbox/get-entries)
                  (map #(select-keys % [:outbox/id :outbox/email]))
                  (sort-by :outbox/id)))))

    (testing "get by type"
      (is (= [id1 id2]
             (->> (outbox/get-entries {:type :email})
                  (map :outbox/id)
                  sort)))
      (is (empty? (outbox/get-entries {:type :foobar}))))

    (testing "get due now, before attempt"
      (is (= [id1 id2]
             (->> (outbox/get-entries {:due-now? true})
                  (map :outbox/id)
                  sort))))

    (testing "attempt failed"
      (let [email (outbox/get-entry-by-id id1)]
        (outbox/attempt-failed! email "the error message"))
      (let [email (outbox/get-entry-by-id id1)
            unrelated (outbox/get-entry-by-id id2)]
        (is (= {:outbox/id id1
                :outbox/latest-error "the error message"
                :outbox/backoff (Duration. 20000)}
               (select-keys email [:outbox/id
                                   :outbox/latest-error
                                   :outbox/backoff])))
        (is (instance? DateTime (:outbox/next-attempt email)))
        (is (instance? DateTime (:outbox/latest-attempt email)))
        (is (nil? (:outbox/latest-attempt unrelated)))))

    (testing "get due now, after attempt"
      (is (= [id2] ; email id1 should be scheduled in future
             (->> (outbox/get-entries {:due-now? true})
                  (map :outbox/id)
                  sort))))

    (testing "all attempts failed"
      (let [email (outbox/get-entry-by-id id1)]
        (try
          (DateTimeUtils/setCurrentMillisFixed (.getMillis (:outbox/deadline email)))
          ;; failed attempt after the deadline
          (outbox/attempt-failed! email "the error message")
          (finally
            (DateTimeUtils/setCurrentMillisSystem))))
      (let [email (outbox/get-entry-by-id id1)]
        (is email)
        (is (nil? (:outbox/next-attempt email)))))

    (testing "attempt succeeded"
      (outbox/attempt-succeeded! id1)
      (is (= [id2]
             (->> (outbox/get-entries)
                  (map :outbox/id)
                  sort))
          "successfully sent emails should be removed from the outbox"))))
