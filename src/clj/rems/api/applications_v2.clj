(ns rems.api.applications-v2
  (:require [rems.db.core :as db]))

(defn api-get-application-v2 [user-id application-id]
  (let [events (db/get-application-events {:application application-id})]
    (when (not (empty? events))
      {:id application-id
       :events events})))
