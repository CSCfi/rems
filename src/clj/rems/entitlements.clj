(ns rems.entitlements
  (:require [compojure.core :refer [GET POST defroutes]]
            [clj-time.format :as format]
            [rems.db.core :as db]
            [rems.text :as text]
            [ring.util.http-response :as response]))

(defn- get-entitlements-for-export []
  ;; TODO auth
  (let [ents (db/get-entitlements-for-export)]
    (with-out-str
      (println "resource,user,start")
      (doseq [e ents]
        (println (:resid e) \, (:userid e) \, (text/localize-time (:start e)))))))

(defroutes entitlements-routes
  (GET "/entitlements.csv" []
       (response/content-type
        {:status 200
         :body (get-entitlements-for-export)}
        "text/csv")))
