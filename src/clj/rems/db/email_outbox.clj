(ns rems.db.email-outbox
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing]]
            [rems.db.core :as db]
            [rems.db.pg-util :refer [pg-interval->joda-duration joda-duration->pg-interval]]
            [rems.json :as json])
  (:import [org.joda.time Duration DateTime]))

(def ^Duration initial-backoff (Duration/standardSeconds 10))
(def ^Duration max-backoff (Duration/standardHours 12))

(defn put! [{:keys [email deadline]}]
  (:id (db/put-to-email-outbox! {:email (json/generate-string email)
                                 :backoff (joda-duration->pg-interval initial-backoff)
                                 :deadline deadline})))

(defn- fix-row-from-db [row]
  {:email-outbox/id (:id row)
   :email-outbox/email (json/parse-string (:email row))
   :email-outbox/created (:created row)
   :email-outbox/next-attempt (:next_attempt row)
   :email-outbox/latest-attempt (:latest_attempt row)
   :email-outbox/latest-error (:latest_error row)
   :email-outbox/backoff (pg-interval->joda-duration (:backoff row))
   :email-outbox/deadline (:deadline row)})

(defn get-emails
  ([]
   (get-emails nil))
  ([{:keys [ids due-now?]}]
   (->> (db/get-email-outbox {:ids ids
                              :due-now? due-now?})
        (map fix-row-from-db))))

(defn get-email-by-id [id]
  (first (get-emails {:ids [id]})))

(defn- next-attempt [email now error]
  (let [^DateTime next-attempt (-> now (.plus (:email-outbox/backoff email)))
        ^DateTime deadline (:email-outbox/deadline email)
        ^Duration backoff (-> (:email-outbox/backoff email) (.multipliedBy 2))]
    (assoc email
           :email-outbox/latest-attempt now
           :email-outbox/latest-error error
           :email-outbox/next-attempt (when (-> now (.isBefore deadline))
                                        (if (-> next-attempt (.isAfter deadline))
                                          deadline
                                          next-attempt))
           :email-outbox/backoff (if (-> backoff (.isLongerThan max-backoff))
                                   max-backoff
                                   backoff))))

(deftest test-next-attempt
  (let [now (DateTime. 1000)
        deadline (DateTime. 666000000)]
    (testing "basic case"
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt (DateTime. 3000) ; now + backoff
              :email-outbox/backoff (Duration. 4000) ; 2 * backoff
              :email-outbox/deadline deadline
              :unrelated-keys "should be kept"}
             (next-attempt {:email-outbox/backoff (Duration. 2000)
                            :email-outbox/deadline deadline
                            :unrelated-keys "should be kept"}
                           now "the error"))))

    (testing "max backoff reached"
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt (.plus now (.minus max-backoff 1))
              :email-outbox/backoff max-backoff
              :email-outbox/deadline deadline}
             (next-attempt {:email-outbox/backoff (.minus max-backoff 1)
                            :email-outbox/deadline deadline}
                           now "the error"))
          "max -1")
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt (.plus now max-backoff)
              :email-outbox/backoff max-backoff
              :email-outbox/deadline deadline}
             (next-attempt {:email-outbox/backoff max-backoff
                            :email-outbox/deadline deadline}
                           now "the error"))
          "exactly max")))

  (testing "final attempt at deadline"
    (let [now (DateTime. 9500)
          deadline (DateTime. 10000)]
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt deadline
              :email-outbox/backoff (Duration. 4000)
              :email-outbox/deadline deadline}
             (next-attempt {:email-outbox/backoff (Duration. 2000)
                            :email-outbox/deadline deadline}
                           now "the error")))))

  (testing "deadline reached"
    (let [now (DateTime. 10000)
          deadline (DateTime. 10000)]
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt nil
              :email-outbox/backoff (Duration. 4000)
              :email-outbox/deadline deadline}
             (next-attempt {:email-outbox/backoff (Duration. 2000)
                            :email-outbox/deadline deadline}
                           now "the error"))
          "exactly deadline"))
    (let [now (DateTime. 10500)
          deadline (DateTime. 10000)]
      (is (= {:email-outbox/latest-attempt now
              :email-outbox/latest-error "the error"
              :email-outbox/next-attempt nil
              :email-outbox/backoff (Duration. 4000)
              :email-outbox/deadline deadline}
             (next-attempt {:email-outbox/backoff (Duration. 2000)
                            :email-outbox/deadline deadline}
                           now "the error"))
          "after deadline"))))

(defn attempt-failed! [email error]
  (let [email (next-attempt email (time/now) error)]
    (db/email-outbox-attempt-failed! {:id (:email-outbox/id email)
                                      :latest_attempt (:email-outbox/latest-attempt email)
                                      :latest_error (:email-outbox/latest-error email)
                                      :next_attempt (:email-outbox/next-attempt email)
                                      :backoff (joda-duration->pg-interval (:email-outbox/backoff email))
                                      :deadline (:email-outbox/deadline email)})
    email))

(defn attempt-succeeded! [id]
  (db/email-outbox-attempt-succeeded! {:id id}))
