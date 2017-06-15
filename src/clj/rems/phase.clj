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

   (example "phase with localized names for each phase"
            (phases [{:id :alpha :phase :apply :completed? true :text :t.phases/apply}
                     {:id :beta :phase :approve :active? true :text :t.phases/approve}
                     {:id :gamma :phase :result :text :t.phases/approved}]))

   (example "phase with rejected application"
            (phases [{:id :alpha :phase :apply :completed? true :text :t.phases/apply}
                     {:id :beta :phase :approve :completed? true :rejected? true :text :t.phases/approve}
                     {:id :gamma :phase :result :completed? true :rejected? true :text :t.phases/rejected}]))

   (example "phase with closed application"
            (phases [{:id :alpha :phase :apply :closed? true :text :t.phases/apply}
                     {:id :beta :phase :approve :closed? true :text :t.phases/approve}
                     {:id :gamma :phase :result :closed? true :text :t.phases/approved}]))
   ))
