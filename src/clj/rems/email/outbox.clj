(ns rems.email.outbox
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing]]
            [rems.db.core :as db]
            [rems.db.pg-util :refer [pg-interval->joda-duration joda-duration->pg-interval]]
            [rems.json :as json]
            [rems.util :refer [getx]]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time Duration DateTime]))

(def ^Duration initial-backoff (Duration/standardSeconds 10))
(def ^Duration max-backoff (Duration/standardHours 12))

(defn put! [data]
  (:id (db/put-to-outbox! {:outboxdata (json/generate-string (assoc data
                                                                    :outbox/type :email
                                                                    :outbox/created (DateTime/now)
                                                                    :outbox/next-attempt (DateTime/now)
                                                                    :outbox/latest-error nil
                                                                    :outbox/latest-attempt nil
                                                                    :outbox/backoff initial-backoff))})))

(defn update! [data]
  (db/update-outbox! {:id (getx data :outbox/id)
                      :outboxdata (json/generate-string (dissoc data :outbox/id))}))


(def OutboxData
  {(s/optional-key :outbox/id) s/Int
   :outbox/type (s/enum :email)
   :outbox/backoff Duration
   :outbox/created DateTime
   :outbox/deadline DateTime
   :outbox/next-attempt (s/maybe DateTime)
   :outbox/latest-attempt (s/maybe DateTime)
   :outbox/latest-error (s/maybe s/Str)
   s/Keyword s/Any})

(def ^:private coerce-outboxdata
  (coerce/coercer! OutboxData (fn [schema]
                                (if (= schema Duration)
                                  #(Duration. (long %))
                                  (json/coercion-matcher schema)))))


(defn- fix-row-from-db [row]
  (-> (:outboxdata row)
      json/parse-string
      coerce-outboxdata
      (assoc :outbox/id (:id row))))

(defn- next-attempt-now? [data]
  (. (:outbox/next-attempt data) isBefore (DateTime/now)))

(defn get-emails
  ([]
   (get-emails nil))
  ([{:keys [ids due-now?]}]
   (let [rows (->> (db/get-outbox {:ids ids})
                   (map fix-row-from-db))]
     (if due-now?
       (filter next-attempt-now? rows) ;; TODO move to db?
       rows))))

(defn get-email-by-id [id]
  (first (get-emails {:ids [id]})))

(defn- next-attempt [email now error]
  (let [^DateTime next-attempt (-> now (.plus (:outbox/backoff email)))
        ^DateTime deadline (:outbox/deadline email)
        ^Duration backoff (-> (:outbox/backoff email) (.multipliedBy 2))]
    (assoc email
           :outbox/latest-attempt now
           :outbox/latest-error error
           :outbox/next-attempt (when (-> now (.isBefore deadline))
                                        (if (-> next-attempt (.isAfter deadline))
                                          deadline
                                          next-attempt))
           :outbox/backoff (if (-> backoff (.isLongerThan max-backoff))
                                   max-backoff
                                   backoff))))

(deftest test-next-attempt
  (let [now (DateTime. 1000)
        deadline (DateTime. 666000000)]
    (testing "basic case"
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt (DateTime. 3000) ; now + backoff
              :outbox/backoff (Duration. 4000) ; 2 * backoff
              :outbox/deadline deadline
              :unrelated-keys "should be kept"}
             (next-attempt {:outbox/backoff (Duration. 2000)
                            :outbox/deadline deadline
                            :unrelated-keys "should be kept"}
                           now "the error"))))

    (testing "max backoff reached"
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt (.plus now (.minus max-backoff 1))
              :outbox/backoff max-backoff
              :outbox/deadline deadline}
             (next-attempt {:outbox/backoff (.minus max-backoff 1)
                            :outbox/deadline deadline}
                           now "the error"))
          "max -1")
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt (.plus now max-backoff)
              :outbox/backoff max-backoff
              :outbox/deadline deadline}
             (next-attempt {:outbox/backoff max-backoff
                            :outbox/deadline deadline}
                           now "the error"))
          "exactly max")))

  (testing "final attempt at deadline"
    (let [now (DateTime. 9500)
          deadline (DateTime. 10000)]
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt deadline
              :outbox/backoff (Duration. 4000)
              :outbox/deadline deadline}
             (next-attempt {:outbox/backoff (Duration. 2000)
                            :outbox/deadline deadline}
                           now "the error")))))

  (testing "deadline reached"
    (let [now (DateTime. 10000)
          deadline (DateTime. 10000)]
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt nil
              :outbox/backoff (Duration. 4000)
              :outbox/deadline deadline}
             (next-attempt {:outbox/backoff (Duration. 2000)
                            :outbox/deadline deadline}
                           now "the error"))
          "exactly deadline"))
    (let [now (DateTime. 10500)
          deadline (DateTime. 10000)]
      (is (= {:outbox/latest-attempt now
              :outbox/latest-error "the error"
              :outbox/next-attempt nil
              :outbox/backoff (Duration. 4000)
              :outbox/deadline deadline}
             (next-attempt {:outbox/backoff (Duration. 2000)
                            :outbox/deadline deadline}
                           now "the error"))
          "after deadline"))))

(defn attempt-failed! [email error]
  (let [email (next-attempt email (time/now) error)]
    (update! email)
    email))

(defn attempt-succeeded! [id]
  (db/delete-outbox! {:id id}))
