(ns ^{:added "2.34"} rems.migrations.event-public
  "Migrates event :application/public to :event/public."
  (:require [clojure.set]
            [hugsql.core :as hugsql]
            [rems.json :as json]
            [rems.db.core]))

(def migration-id 20230912072334)

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

(def attributes-up {:application/public :event/public})
(def attributes-down {:event/public :application/public})

(defn migrate-event [event rename-kmap]
  (update event :eventdata clojure.set/rename-keys rename-kmap))

(def event-from-db #(update % :eventdata json/parse-string))

(defn migrate-events! [conn xf attributes]
  (doseq [event (sequence (comp (map event-from-db) xf) (get-events conn))
          :let [new-event (migrate-event event attributes)]]
    (set-event! conn
                (update new-event :eventdata json/generate-string))))

(def old-event? #(boolean? (get-in % [:eventdata :application/public])))
(def new-event? #(boolean? (get-in % [:eventdata :event/public])))

(defn migrate-up [{:keys [conn]}]
  (migrate-events! conn (filter old-event?) attributes-up))

(defn migrate-down [{:keys [conn]}]
  (migrate-events! conn (filter new-event?) attributes-down))

(comment
  (migrate-up {:conn rems.db.core/*db*})
  (migrate-down {:conn rems.db.core/*db*}))
