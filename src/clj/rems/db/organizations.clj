(ns rems.db.organizations
  (:require [clojure.string :as str]
            [rems.db.core :as db]
            [rems.json :as json]))

(defn add-organization! [org]
  (db/add-organization! {:id (:organization/id org)
                         :data (json/generate-string org)})
  (:organization/id org))

(defn- parse-organization [raw]
  (json/parse-string (:data raw)))

(defn get-organizations [& [owner]]
  (->> (db/get-organizations)
       (map parse-organization)
       (filter #(or (nil? owner)
                    (contains? (set (map :userid (:organization/owners %))) owner)))
       (doall)))
