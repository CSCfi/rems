(ns rems.collapsible
  (:require [reagent.core :as r]
            [reagent.impl.util]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(rf/reg-sub ::expanded (fn [db [_ id init]] (true? (get-in db [::id id] init))))

(rf/reg-event-db ::set-expanded (fn [db [_ id expanded?]] (assoc-in db [::id id] (true? expanded?))))
(rf/reg-event-db ::reset-expanded (fn [db [_ id]] (update db ::id dissoc id)))
(rf/reg-event-db ::toggle (fn [db [_ id]] (update-in db [::id id] not)))

(rf/reg-event-fx ::show-and-focus (fn [{:keys [db]} [_ id]]
                                    (when-not (get-in db [::id id])
                                      {:db (assoc-in db [::id id] true)
                                       :dispatch [:rems.focus/focus-input {:selector ".collapse-open"
                                                                           :target (.getElementById js/document id)}]})))

(defn- base-action [id & [{:keys [init]}]]
  (let [state @(rf/subscribe [::expanded id init])]
    {:aria-controls id
     :aria-expanded (if state
                      "true"
                      "false")
     :label (if state
              (text :t.collapse/hide)
              (text :t.collapse/show))}))

(defn toggle-action
  "Action that toggles collapsible state."
  [id & [{:keys [init on-click]}]]
  (assoc (base-action id {:init init})
         :on-click (fn [& args]
                     (rf/dispatch [::toggle id])
                     (some-> on-click (apply args)))))

(defn- show-and-focus! [id]
  (fn [^js event]
    (rf/dispatch [::show-and-focus id])
    (doto event (.preventDefault))))

(defn- show-control [id & [{:keys [label on-click]}]]
  [:div.text-center
   [atoms/action-link (-> (base-action id)
                          (assoc :on-click (comp (or on-click identity) (show-and-focus! id))
                                 :label (or label (text :t.collapse/show))
                                 :class "show-more-link"
                                 :url "#"))]])

(defn- hide-control [id & [{:keys [label on-click]}]]
  [:div.text-center
   [atoms/action-link (-> (base-action id)
                          (assoc :on-click (fn [& args]
                                             (rf/dispatch [::set-expanded id false])
                                             (some-> on-click (apply args)))
                                 :label (or label (text :t.collapse/hide))
                                 :class "show-less-link"
                                 :url "#"))]])

(defn toggle-control
  "A hide/show button that externally toggles the visibility of a collapsible.

   - `:id` id of the controlled collapsible"
  [id & [on-click]]
  (if @(rf/subscribe [::expanded id])
    [hide-control id {:on-click on-click}]
    [show-control id {:on-click on-click}]))

(defn- collapse-block [expanded? collapse & [collapse-hidden]]
  (when (some? collapse)
    (if expanded?
      [:div.collapse-open collapse]
      [:div.collapse-closed collapse-hidden])))

(defn component
  "Collapsible content block that can show hidden content on click.
  Contains distinct border, and location of toggle controls are customizable.
  
  Pass a map of options with the following keys:
  - `:always` component displayed always before collapsible area
  - `:bottom-less-button?` should bottom show less button be shown?
  - `:collapse` component that is toggled displayed or not
  - `:collapse-hidden` component that is displayed when content is collapsed
  - `:footer` component displayed always after collapsible area
  - `:id` unique string
  - `:on-close` triggers the function callback given as an argument when show less is clicked
  - `:on-open` triggers the function callback given as an argument when collapse is toggled open
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area
  - `:top-less-button?` should top show less button be shown?"
  [{:keys [always bottom-less-button? collapse collapse-hidden footer id on-close on-open open? title top-less-button?]
    :or {bottom-less-button? true}}]
  (r/with-let [expanded? (rf/subscribe [::expanded id open?])]
    [:div.collapsible.bordered-collapsible {:id id}
     [:h2.card-header title]
     [:div.collapsible-contents
      always
      (when collapse
        [:<>
         (when (and @expanded? top-less-button?) [hide-control id {:on-click on-close}])
         [collapse-block @expanded? collapse collapse-hidden]
         (when (not @expanded?) [show-control id {:on-click on-open}])
         (when (and @expanded? bottom-less-button?) [hide-control id {:on-click on-close}])])
      footer]]
    (finally
      (rf/dispatch [::reset-expanded id]))))

(defn minimal
  "Collapsible variation that does not have border or title, and controls
  must be provided externally with e.g. `rems.collapsible/toggle-control`.
  
  Pass a map of options with the following keys:
  - `:always` component displayed always before collapsible area
  - `:collapse` component that is toggled displayed or not
  - `:collapse-hidden` component that is displayed when content is collapsed
  - `:class` optional class for wrapping element
  - `:footer` component displayed always after collapsible area
  - `:id` (required) unique id
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area"
  [{:keys [always class collapse collapse-hidden footer id open? title]}]
  (r/with-let [expanded? (rf/subscribe [::expanded id open?])]
    [:div.collapsible (merge {:id id} (when class {:class class}))
     (when title [:h2.card-header title])
     [:div.collapsible-contents
      always
      [collapse-block @expanded? collapse collapse-hidden]
      footer]]
    (finally
      (rf/dispatch [::reset-expanded id]))))

(defn expander
  "Collapsible variation where simple title is the toggle control.
  
  Pass a map of options with the following keys:
  - `:collapse` component that is toggled displayed or not
  - `:id` (required) unique id
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area"
  [{:keys [collapse id open? title]}]
  (r/with-let [expanded? (rf/subscribe [::expanded id open?])]
    [:div.collapsible.expander-collapsible {:id id}
     [atoms/action-link (assoc (toggle-action id {:init open?})
                               :class "expander-toggle"
                               :label [:div.d-flex.align-items-center.gap-1.pointer
                                       [:i.fa.fa-chevron-down.animate-transform {:class (when @expanded? "rotate-180")}]
                                       title]
                               :url "#")]
     [:div.collapsible-contents
      [collapse-block @expanded? collapse]]]
    (finally
      (rf/dispatch [::reset-expanded id]))))

(defn guide
  []
  [:div
   (component-info component)
   (example "default use"
            [component {:id "standard-collapsible-1"
                        :title "Standard"
                        :always [:p "I am content that is always visible"]
                        :collapse [:p "I am content that you can hide"]}])
   (example "no hide controls"
            [component {:id "standard-collapsible-2"
                        :title "Focus on show"
                        :always [:p "Hidden input receives focus on show"]
                        :collapse [:input]}])
   (example "no collapse content"
            [component {:id "standard-collapsible-3"
                        :title "No collapse"
                        :always [:p "I am content that is always visible"]
                        :footer [:div.solid-group "I am the footer that is always visible"]}])
   (example "all options"
            [component {:id "standard-collapsible-4"
                        :title "Highly customized"
                        :open? true
                        :top-less-button? true
                        :bottom-less-button? true
                        :on-open #(js/console.log "hello")
                        :on-close #(js/console.log "bye")
                        :always [:div.solid-group
                                 [:b "Custom controls:"]
                                 [rems.collapsible/toggle-control "standard-collapsible-4"]]
                        :collapse (into [:div] (repeat 3 [:p "I am long content that you can hide"]))
                        :collapse-hidden [:p "I am content that is only visible when collapsed"]
                        :footer [:div.solid-group "I am the footer that is always visible"]}])

   (component-info minimal)
   (example "default use"
            [minimal {:id "minimal-collapsible-1"
                      :always [rems.collapsible/toggle-control "minimal-collapsible-1"]
                      :collapse [:p "I am content that you can hide"]}])
   (example "footer controls"
            [minimal {:id "minimal-collapsible-2"
                      :footer [rems.collapsible/toggle-control "minimal-collapsible-2"]
                      :collapse [:p "I am content that you can hide"]}])
   (example "all options"
            [minimal {:id "minimal-collapsible-3"
                      :open? true
                      :always [rems.collapsible/toggle-control "minimal-collapsible-3"]
                      :collapse (into [:div] (repeat 3 [:p "I am long content that you can hide"]))
                      :collapse-hidden [:p "I am content that is only visible when collapsed"]
                      :footer [rems.collapsible/toggle-control "minimal-collapsible-3"]}])

   (component-info expander)
   (example "defaults"
            [expander {:id "expander-collapsible-1"
                       :title "The whole header is clickable"
                       :collapse (into [:div] (repeat 3 [:p "I am content that you can hide"]))}])
   (example "all options"
            [expander {:id "expander-collapsible-2"
                       :title [:h3.m-0 "Initially open"]
                       :collapse (into [:div] (repeat 3 [:p "I am content that you can hide"]))
                       :open? true}])])
