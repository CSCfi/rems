(ns rems.api.audit-log
  (:require [compojure.api.sweet :refer :all]
            [rems.db.core :as db]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema AuditLogEntry
  {:time DateTime
   :path s/Str
   :method s/Str
   :apikey (s/maybe s/Str)
   :userid (s/maybe s/Str)
   :status s/Str})

(def audit-log-api
  (context "/audit-log" []
    (GET "/" []
      :summary "Get audit log entries"
      :roles #{:reporter}
      :return [AuditLogEntry]
      (db/get-audit-log {}))))
