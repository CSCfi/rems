(ns rems.phase
  (:require [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn phases
  "Component for phase progress bar.

  Phases can contain the following keys:
  :phase      unique id of the phase
  :active?    is the phase currently active
  :completed? has the phase been completed
  :approved?  is the phase an approve phase that has been approved
  :rejected?  is the phase an approve phase that has been rejected
  :closed?    has the (application) been closed"
  [phases]
  (into [:div.phases]
        (for [phase phases]
          [:div.phase {:id (str (name (:phase phase)) "-phase")
                       :class (str (when (:active? phase) "active ")
                                   (when (:rejected? phase) "rejected ")
                                   (when (:completed? phase) "completed ")
                                   (when (:closed? phase) "closed "))}
           [:span
            (cond (:rejected? phase) [:i.fa.fa-times {:aria-label (text :t.phases/phase-rejected)}]
                  (:completed? phase) [:i.fas.fa-check {:aria-label (text :t.phases/phase-completed)}]
                  (:active? phase) [:i.fa.fa-chevron-right {:aria-label (text :t.phases/phase-active)}]
                  ;; NVDA will not read the aria label if the element is empty, so we need an invisible icon
                  (:closed? phase) [:i.fa.fa-chevron-right {:aria-label (text :t.phases/phase-closed)
                                                            :style {:color "rgba(0,0,0,0)"}}]
                  :else [:i.fa.fa-chevron-right {:aria-label (text :t.phases/phase-pending)
                                                 :style {:color "rgba(0,0,0,0)"}}])
            "\u00a0"
            (if (:text phase)
              (text (:text phase))
              (:phase phase))]])))

(defn guide
  []
  [:div
   (component-info phases)
   (example "phase, nothing yet"
            [phases [{:phase :alpha} {:phase :beta} {:phase :gamma} {:phase :delta}]])
   (example "phase 0 / 4, first active"
            [phases [{:phase :alpha :active? true} {:phase :beta} {:phase :gamma} {:phase :delta}]])
   (example "phase 1 / 4, second active"
            [phases [{:phase :alpha :completed? true} {:phase :beta :active? true} {:phase :gamma} {:phase :delta}]])
   (example "phase 2 / 4, third active"
            [phases [{:phase :alpha :completed? true} {:phase :beta :completed? true} {:phase :gamma :active? true} {:phase :delta}]])
   (example "phase 3 / 4, fourth active"
            [phases [{:phase :alpha :completed? true} {:phase :beta :completed? true} {:phase :gamma :completed? true} {:phase :delta :active? true}]])
   (example "phase 4 / 4, all done"
            [phases [{:phase :alpha :completed? true} {:phase :beta :completed? true} {:phase :gamma :completed? true} {:phase :delta :completed? true}]])

   (example "phase 2 / 3, second active"
            [phases [{:phase :alpha :completed? true} {:phase :beta :active? true} {:phase :gamma}]])

   (example "phase with localized names for each phase"
            [phases [{:phase :apply :completed? true :text :t.phases/apply}
                     {:phase :approve :active? true :text :t.phases/approve}
                     {:phase :result :text :t.phases/approved}]])

   (example "phase with approved application"
            [:div.state-approved
             [phases [{:phase :apply :completed? true :text :t.phases/apply}
                      {:phase :approve :completed? true :text :t.phases/approve}
                      {:phase :result :completed? true :approved? true :text :t.phases/approved}]]])

   (example "phase with rejected application"
            [:div.state-rejected
             [phases [{:phase :apply :completed? true :text :t.phases/apply}
                      {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
                      {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]]])

   (example "phase with closed application"
            [phases [{:phase :apply :closed? true :text :t.phases/apply}
                     {:phase :approve :closed? true :text :t.phases/approve}
                     {:phase :result :closed? true :text :t.phases/approved}]])])
