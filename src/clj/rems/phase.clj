(ns rems.phase
  (:require [rems.guide :refer :all]
            [rems.text :refer :all]))

(defn phases
  "Component for phase progress bar.

  done? - is this phase completed"
  [phases]
  (into [:div.phases]
        (for [phase phases]
          [:div.phase {:class (str (when (:active? phase) "active ")
                                   (when (:rejected? phase) "rejected ")
                                   (when (:completed? phase) "completed ")
                                   (when (:returned? phase) "returned ")
                                   (when (:closed? phase) "closed "))}
           [:span (cond (:rejected? phase)  [:i.fa.fa-times]
                        (:completed? phase) [:i.fa.fa-check])
            (if (:text phase)
              (text (:text phase))
              (:id phase))]])))
