(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [clojure.string :refer [join]]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.db.core :as db]
            [rems.roles :refer [has-roles?]]
            [rems.text :as text]))

(defn get-entitlements-for-export []
  (when-not (has-roles? :approver)
    (throw-unauthorized))
  (let [ents (db/get-entitlements-for-export)]
    (with-out-str
      (println "resource,application,user,start")
      (doseq [e ents]
        (println (join "," [(:resid e) (:catappid e) (:userid e) (text/localize-time (:start e))]))))))
