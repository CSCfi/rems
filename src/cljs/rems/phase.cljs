(ns rems.phase
  (:require [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn phases
  "Component for phase progress bar.

  state         - the overall active state
  phases        - seq where each phase has the following structure
    :phase      - unique id of the phase
    :active?    - is the phase currently active
    :completed? - has the phase been completed
    :approved?  - is the phase an approve phase that has been approved
    :rejected?  - is the phase an approve phase that has been rejected
    :revoked?   - is the phase an approve phase that has been revoked
    :closed?    - has the (application) been closed"
  [state phases]
  (into [:div.phases {:class (str "state-" (name state))}]
        (for [phase phases]
          [:div.phase {:id (str (name (:phase phase)) "-phase")
                       :class (str (when (:active? phase) "active ")
                                   (when (:rejected? phase) "rejected ")
                                   (when (:revoked? phase) "revoked ")
                                   (when (:completed? phase) "completed ")
                                   (when (:closed? phase) "closed "))}
           [:span
            (cond (:rejected? phase) [:i.fa.fa-times [:span.sr-only (text :t.phases/phase-rejected)]]
                  (:revoked? phase) [:i.fa.fa-times [:span.sr-only (text :t.phases/phase-revoked)]]
                  (:completed? phase) [:i.fas.fa-check [:span.sr-only (text :t.phases/phase-completed)]]
                  (:active? phase) [:i.fa.fa-chevron-right [:span.sr-only (text :t.phases/phase-active)]]
                  ;; NVDA will not read the aria label if the element is empty, so we need an invisible icon
                  (:closed? phase) [:i.fa.fa-chevron-right {:style {:color "rgba(0,0,0,0)"}}
                                    [:span.sr-only (text :t.phases/phase-closed)]]
                  :else [:i.fa.fa-chevron-right {:style {:color "rgba(0,0,0,0)"}}
                         [:span.sr-only (text :t.phases/phase-pending)]])
            "\u00a0"
            (if (:text phase)
              (text (:text phase))
              (:phase phase))]])))

(defn guide
  []
  [:div
   (component-info phases)
   (example "phase with localized names for each phase"
            [phases :submitted [{:phase :apply :completed? true :text :t.phases/apply}
                                {:phase :approve :active? true :text :t.phases/approve}
                                {:phase :result :text :t.phases/approved}]])

   (example "phase with approved application"
            [phases :approved [{:phase :apply :completed? true :text :t.phases/apply}
                               {:phase :approve :completed? true :text :t.phases/approve}
                               {:phase :result :completed? true :approved? true :text :t.phases/approved}]])

   (example "phase with rejected application"
            [phases :rejected [{:phase :apply :completed? true :text :t.phases/apply}
                               {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
                               {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]])

   (example "phase with revoked application"
            [phases :revoked [{:phase :apply :completed? true :text :t.phases/apply}
                              {:phase :approve :completed? true :approved? true :text :t.phases/approve}
                              {:phase :result :completed? true :revoked? true :text :t.phases/revoked}]])

   (example "phase with closed application"
            [phases :closed [{:phase :apply :closed? true :text :t.phases/apply}
                             {:phase :approve :closed? true :text :t.phases/approve}
                             {:phase :result :closed? true :text :t.phases/approved}]])])
