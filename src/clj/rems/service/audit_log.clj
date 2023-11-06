(ns rems.service.audit-log
  (:require [rems.db.audit-log :as audit-log]))


(defn get-audit-log [params]
  (audit-log/get-audit-log params))

(defn add-to-audit-log! [entry]
  (audit-log/add-to-audit-log! entry))
