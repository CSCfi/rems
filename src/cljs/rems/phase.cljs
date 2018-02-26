(ns rems.phase
  (:require [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO compute these on the server side?
(defn get-application-phases [state]
  (cond (= state "rejected")
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (= state "approved")
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (= state "closed")
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{"draft" "returned" "withdrawn"} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (= "applied" state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]))

(defn phases
  "Component for phase progress bar.

  Phases can contain the following keys:
  :id         unique id of the phase
  :active?    is the phase currently active
  :completed? has the phase been completed
  :approved?  is the phase an approve phase that has been approved
  :rejected?  is the phase an approve phase that has been rejected
  :closed?    has the (application) been closed"
  [phases]
  (into [:div.phases]
        (for [phase phases]
          [:div.phase {:class (str (when (:active? phase) "active ")
                                   (when (:rejected? phase) "rejected ")
                                   (when (:completed? phase) "completed ")
                                   (when (:closed? phase) "closed "))}
           [:span (cond (:rejected? phase)  [:i.fa.fa-times]
                        (:completed? phase) [:i.fa.fa-check])
            (if (:text phase)
              (text (:text phase))
              (:id phase))]])))

(defn guide
  []
  [:div
   (component-info phases)
   (example "phase, nothing yet"
            [phases [{:id :alpha} {:id :beta} {:id :gamma} {:id :delta}]])
   (example "phase 0 / 4, first active"
            [phases [{:id :alpha :active? true} {:id :beta} {:id :gamma} {:id :delta}]])
   (example "phase 1 / 4, second active"
            [phases [{:id :alpha :completed? true} {:id :beta :active? true} {:id :gamma} {:id :delta}]])
   (example "phase 2 / 4, third active"
            [phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :active? true} {:id :delta}]])
   (example "phase 3 / 4, fourth active"
            [phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :completed? true} {:id :delta :active? true}]])
   (example "phase 4 / 4, all done"
            [phases [{:id :alpha :completed? true} {:id :beta :completed? true} {:id :gamma :completed? true} {:id :delta :completed? true}]])

   (example "phase 2 / 3, second active"
            [phases [{:id :alpha :completed? true} {:id :beta :active? true} {:id :gamma}]])

   (example "phase with localized names for each phase"
            [phases [{:id :alpha :phase :apply :completed? true :text :t.phases/apply}
                     {:id :beta :phase :approve :active? true :text :t.phases/approve}
                     {:id :gamma :phase :result :text :t.phases/approved}]])

   (example "phase with approved application"
            [:div.state-approved
             [phases [{:id :alpha :phase :apply :completed? true :text :t.phases/apply}
                      {:id :beta :phase :approve :completed? true :text :t.phases/approve}
                      {:id :gamma :phase :result :completed? true :approved? true :text :t.phases/approved}]]])

   (example "phase with rejected application"
            [:div.state-rejected
             [phases [{:id :alpha :phase :apply :completed? true :text :t.phases/apply}
                      {:id :beta :phase :approve :completed? true :rejected? true :text :t.phases/approve}
                      {:id :gamma :phase :result :completed? true :rejected? true :text :t.phases/rejected}]]])

   (example "phase with closed application"
            [phases [{:id :alpha :phase :apply :closed? true :text :t.phases/apply}
                     {:id :beta :phase :approve :closed? true :text :t.phases/approve}
                     {:id :gamma :phase :result :closed? true :text :t.phases/approved}]])
   ])
