(ns rems.common.duo
  (:require [medley.core :refer [distinct-by]]))

(defn duo-restriction-label [restriction]
  (keyword "t.duo.restriction" (name restriction)))

(defn duo-validation-summary [statuses]
  (when-let [statuses (not-empty (remove #{:duo/not-found} statuses))]
    (or (some #{:duo/not-compatible} statuses)
        (some #{:duo/needs-manual-validation} statuses)
        :duo/compatible)))

(defn unmatched-duos [duo-matches]
  (->> duo-matches
       (filter (comp #{:duo/not-found} :valid :duo/validation))
       (distinct-by :duo/id)))

