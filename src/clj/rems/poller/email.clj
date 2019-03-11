(ns rems.poller.email
  "Sending emails based on application events."
  (:require [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.core :as db]))

(defn get-state []
  (or (json/parse-string (:state (db/get-poller-state {:name (name ::state)})))
      {:event/id -1}))

(defn set-state! [state]
  (db/set-poller-state! {:name (name ::state) :state (json/generate-string state)}))

(defn run []
  (let [prev-state (get-state)
        events (applications/get-dynamic-application-events-since (:event/id prev-state))]
    (prn :START prev-state)
    (when-not (empty? events)
      (doseq [e events]
        (prn e))
      (set-state! {:event/id (:event/id (last events))}))))
