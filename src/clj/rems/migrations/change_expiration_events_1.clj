(ns ^{:added "2.34.1"} rems.migrations.change-expiration-events-1
  "Migrates event `:expires-on` to `:application/expires-on`
  and removes the `:last-activity`.

  Runs the same migration as `rems.migrations.change-expiration-events`,
  but fixes issue where no events would get migrated, and renames :expires-on
  so that migrate-up does not result in schema error due to extra keys."
  (:require [clojure.set]
            [hugsql.core :as hugsql]
            [rems.db.applications]
            [rems.db.core]
            [rems.json :as json]))

(def migration-id 20231103083019)

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
      (clojure.set/rename-keys {:expires-on :application/expires-on})
      (dissoc :last-activity)))

(defn attributes-down [eventdata]
  (-> eventdata
      (clojure.set/rename-keys {:application/expires-on :expires-on})
      (assoc :last-activity (:event/time eventdata)))) ; NB: not the same but it would be difficult to restore

(defn migrate-event [event migrator]
  (update event :eventdata migrator))

(def event-from-db #(update % :eventdata json/parse-string))

(defn migrate-events! [conn xf migrator]
  (doseq [event (sequence (comp (map event-from-db) xf) (get-events conn))
          :let [new-event (migrate-event event migrator)]]
    (set-event! conn
                (update new-event :eventdata json/generate-string))))

(def old-event? #(contains? (:eventdata %) :expires-on))
(def new-event? #(contains? (:eventdata %) :application/expires-on))

(defn migrate-up [{:keys [conn]}]
  (migrate-events! conn (filter old-event?) attributes-up))

(defn migrate-down [{:keys [conn]}]
  (migrate-events! conn (filter new-event?) attributes-down))

(comment
  (migrate-up {:conn rems.db.core/*db*})
  (migrate-down {:conn rems.db.core/*db*}))
