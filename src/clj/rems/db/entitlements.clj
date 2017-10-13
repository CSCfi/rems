(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.string :refer [join]]
            [clojure.tools.logging :as log]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.roles :refer [has-roles?]]
            [rems.text :as text]))

(defn get-entitlements-for-export
  "Returns a CSV string representing entitlements"
  []
  (when-not (has-roles? :approver)
    (throw-unauthorized))
  (let [ents (db/get-entitlements-for-export)]
    (with-out-str
      (println "resource,application,user,start")
      (doseq [e ents]
        (println (join "," [(:resid e) (:catappid e) (:userid e) (text/localize-time (:start e))]))))))

(defn add-entitlements-for
  "If the given application is approved, add an entitlement to the db
  and call the entitlement REST callback (if defined)."
  [application]
  (when (= "approved" (:state application))
    (db/add-entitlement! {:application (:id application)
                          :user (:applicantuserid application)})
    (when-let [target (get env :entitlements-target)]
      (let [payload {:application (:id application)
                     ;; TODO: resource
                     :user (:applicantuserid application)}]
        (log/infof "Posting entitlement %s to %s" payload target)
        (try
          (http/post target
                     {:body (cheshire/generate-string payload)
                      :content-type :json
                      :timeout 2500})
          (catch Exception e
            (log/error "POST failed" e)))))))
