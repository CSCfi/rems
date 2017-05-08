(ns rems.phase
  (:require [rems.guide :refer :all]))

(defn phases
  "Component for phase progress bar.

  done? - is this phase completed"
  [phases]
  (into [:div.phases]
        (for [phase phases]
          [:div.phase {:class (cond (:active? phase) "active"
                                    (:completed? phase) "completed")}
           [:span (when (:completed? phase) [:i.fa.fa-check]) (or (:phase phase) (:id phase))]])))

(defn guide
  []
  (list
   (example "phase, nothing yet"
            (phases [{:id :alpha} {:id :beta} {:id :gamma} {:id :delta}]))
   (example "phase 0 / 4, first active"
            (phases [{:id :alpha :active? true} {:id :beta} {:id :gamma} {:id :delta}]))
   (example "phase 1 / 4, second active"
            (phases [{:id :alpha :completed? true} {:id :beta :active? true} {:id :gamma} {:id :delta}]))
   (example "phase 2 / 4, third active"
            (phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :active? true} {:id :delta}]))
   (example "phase 3 / 4, fourth active"
            (phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :completed? true} {:id :delta :active? true}]))
   (example "phase 4 / 4, all done"
            (phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :completed? true} {:id :delta :completed? true}]))

   (example "phase 2 / 3, second active"
            (phases [{:id :alpha :completed? true} {:id :beta :active? true} {:id :gamma}]))

   (example "phase with separate names for each phase"
            (phases [{:id :alpha :phase :apply :completed? true} {:id :beta :phase :approve :active? true} {:id :gamma :phase :approved}]))
   ))
