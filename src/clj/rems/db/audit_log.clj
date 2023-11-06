(ns rems.db.audit-log
  (:require [rems.db.core :as db]))

(defn get-audit-log [params]
  (db/get-audit-log params))

(defn add-to-audit-log! [entry]
  (db/add-to-audit-log! entry))

(defn update-audit-log! [entry]
  (db/update-audit-log! entry))
