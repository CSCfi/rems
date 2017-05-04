(ns rems.phase
  (:require [rems.guide :refer :all]))

(defn phases
  "Component for phase progress bar.

  done? - is this phase completed"
  [done? phases]
  (into [:div.phases]
        (for [phase phases]
          [:div.phase {:class (when (done? phase) "ok")}
           [:span phase]])))

(defn guide
  []
  (list
   (example "phase 1 / 4"
            (phases #{"alpha"} ["alpha" "beta" "gamma" "delta"]))
   (example "phase 2 / 4"
            (phases #{"alpha" "beta"} ["alpha" "beta" "gamma" "delta"]))
   (example "phase 3 / 4"
            (phases #{"alpha" "beta" "gamma"} ["alpha" "beta" "gamma" "delta"]))
   (example "phase 4 / 4"
            (phases #{"alpha" "beta" "gamma" "delta"} ["alpha" "beta" "gamma" "delta"]))

   (example "phase 2 / 3"
            (phases #{"alpha" "beta"} ["alpha" "beta" "gamma"]))

   (example "phase with complicated flow"
            (phases #{"apply" "review"} ["apply" "review" "approve" "approve" "done"]))))
