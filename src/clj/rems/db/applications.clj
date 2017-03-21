(ns rems.db.applications
  (:require [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]))

(defn get-applications []
  (doall
   (for [a (db/get-applications)]
     (assoc a :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))
