(ns rems.common.duo
  (:require [medley.core :refer [distinct-by]]))

(def duo-restriction-label
  {:collaboration :t.duo.restriction/collaboration
   :date :t.duo.restriction/date
   :institute :t.duo.restriction/institute
   :location :t.duo.restriction/location
   :mondo :t.duo.restriction/mondo
   :months :t.duo.restriction/months
   :project :t.duo.restriction/project
   :topic :t.duo.restriction/topic
   :users :t.duo.restriction/users})

(defn duo-validation-summary [statuses]
  (when-let [statuses (not-empty (remove #{:duo/not-found} statuses))]
    (or (some #{:duo/not-compatible} statuses)
        (some #{:duo/needs-manual-validation} statuses)
        :duo/compatible)))

(defn unmatched-duos [duo-matches]
  (->> duo-matches
       (filter (comp #{:duo/not-found} :validity :duo/validation))
       (distinct-by :duo/id)))

