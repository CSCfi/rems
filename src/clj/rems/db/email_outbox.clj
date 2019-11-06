(ns rems.db.email-outbox
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(defn put! [{:keys [email attempts]}]
  (:id (db/put-to-email-outbox! {:email (json/generate-string email)
                                 :attempts attempts})))

(defn- fix-row-from-db [row]
  {:email-outbox/id (:id row)
   :email-outbox/email (json/parse-string (:email row))
   :email-outbox/created (:created row)
   :email-outbox/latest-attempt (:latest_attempt row)
   :email-outbox/latest-error (:latest_error row)
   :email-outbox/remaining-attempts (:remaining_attempts row)})

(defn get-emails
  ([]
   (get-emails nil))
  ([{:keys [ids]}]
   (->> (db/get-email-outbox {:ids ids})
        (map fix-row-from-db))))
