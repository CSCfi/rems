(ns ^{:added "2.34"} rems.migrations.change-expiration-events
  "Migrates event `:expires-on` to `:application/expires-on`
  and removes the `:last-modified`."
  (:require [clojure.set]
            [hugsql.core :as hugsql]
            [medley.core :refer [assoc-some]]
            [rems.db.applications]
            [rems.db.core]
            [rems.json :as json]))

(def migration-id 20231026151640)

(declare get-events set-event!)
(hugsql/def-db-fns-from-string
  "
-- :name get-events :? :*
SELECT id, eventdata::TEXT
FROM application_event;

-- :name set-event! :!
UPDATE application_event
SET eventdata = :eventdata::jsonb
WHERE id = :id;
")

(defn attributes-up [eventdata]
  (-> eventdata
      (assoc-some :application/expires-on (:expires-on eventdata))
      (dissoc :last-activity)))

(defn attributes-down [eventdata]
  (-> eventdata
      (assoc-some :expires-on (:application/expires-on eventdata))
      (assoc :last-activity (:event/time eventdata)))) ; NB: not the same but it would be difficult to restore

(defn migrate-event [event migrator]
  (update event :eventdata migrator))

(def event-from-db #(update % :eventdata json/parse-string))

(defn migrate-events! [conn xf migrator]
  (doseq [event (sequence (comp (map event-from-db) xf) (get-events conn))
          :let [new-event (migrate-event event migrator)]]
    (set-event! conn
                (update new-event :eventdata json/generate-string))))

(def old-event? #(boolean? (get-in % [:eventdata :expires-on])))
(def new-event? #(boolean? (get-in % [:eventdata :application/expires-on])))

(defn migrate-up [{:keys [conn]}]
  (migrate-events! conn (filter old-event?) attributes-up))

(defn migrate-down [{:keys [conn]}]
  (migrate-events! conn (filter new-event?) attributes-down))

(comment
  (migrate-up {:conn rems.db.core/*db*})
  (migrate-down {:conn rems.db.core/*db*}))
