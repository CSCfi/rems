(ns rems.db.outbox
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing]]
            [rems.db.core :as db]
            [rems.db.pg-util :refer [pg-interval->joda-duration joda-duration->pg-interval]]
            [rems.json :as json]
            [rems.util :refer [getx]]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time Duration DateTime]))

;; TODO: the meaning of the fields should be documented
(def OutboxData
  {(s/optional-key :outbox/id) s/Int
   :outbox/type (s/enum :email :entitlement-post :event-notification)
   :outbox/backoff Duration
   :outbox/created DateTime
   :outbox/deadline DateTime
   :outbox/next-attempt (s/maybe DateTime)
   :outbox/latest-attempt (s/maybe DateTime)
   :outbox/latest-error (s/maybe s/Any)
   (s/optional-key :outbox/email) s/Any
   (s/optional-key :outbox/entitlement-post) s/Any
   (s/optional-key :outbox/event-notification) s/Any})

(def ^Duration initial-backoff (Duration/standardSeconds 10))
(def ^Duration max-backoff (Duration/standardHours 12))

(def ^:private validate-outbox-data
  (s/validator OutboxData))

(defn put! [data]
  (let [amended (assoc data
                       :outbox/created (DateTime/now)
                       :outbox/next-attempt (DateTime/now)
                       :outbox/latest-error nil
                       :outbox/latest-attempt nil
                       :outbox/backoff initial-backoff)]
    (:id (db/put-to-outbox! {:outboxdata (json/generate-string
                                          (validate-outbox-data amended))}))))

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

(defn- next-attempt-now? [now entry]
  (when-let [next (:outbox/next-attempt entry)]
    (not (. next isAfter now))))

(deftest test-next-attempt-now?
  (is (next-attempt-now? (DateTime. 1234) {:outbox/next-attempt (DateTime. 1234)}))
  (is (not (next-attempt-now? (DateTime. 1233) {:outbox/next-attempt (DateTime. 1234)})))
  (is (next-attempt-now? (DateTime. 1235) {:outbox/next-attempt (DateTime. 1234)}))
  (is (not (next-attempt-now? (DateTime. 1235) {:outbox/next-attempt nil}))))

(defn get-entries
  ([]
   (get-entries nil))
  ([{:keys [type ids due-now?]}]
   (cond->> (db/get-outbox {:ids ids})
     true (map fix-row-from-db)
     due-now? (filter (partial next-attempt-now? (DateTime/now))) ;; TODO move to db?
     type (filter #(= type (:outbox/type %))))))

(defn get-due-entries [type]
  (get-entries {:type type :due-now? true}))

(defn get-entry-by-id [id]
  (first (get-entries {:ids [id]})))

(defn- next-attempt [entry now error]
  (let [^DateTime next-attempt (-> now (.plus (:outbox/backoff entry)))
        ^DateTime deadline (:outbox/deadline entry)
        ^Duration backoff (-> (:outbox/backoff entry) (.multipliedBy 2))]
    (assoc entry
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

(defn attempt-failed! [entry error]
  (let [entry (next-attempt entry (time/now) error)]
    (db/update-outbox! {:id (getx entry :outbox/id)
                        :outboxdata (json/generate-string (dissoc entry :outbox/id))})
    entry))

(defn attempt-succeeded! [id]
  (db/delete-outbox! {:id id}))
