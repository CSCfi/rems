(ns rems.db.api-key
  (:require [rems.db.core :as db]))

(defn valid? [key]
  (not (nil? (db/get-api-key {:apikey key}))))
