(ns rems.collapsible
  (:require [better-cond.core :as b]
            [medley.core :refer [assoc-some]]
            [reagent.core :as r]
            [reagent.format :as rfmt]
            [reagent.impl.util]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(rf/reg-sub ::expanded (fn [db [_ id]] (true? (get-in db [::id id]))))

(defn- close-others-in-group
  "Finds all collapsibles that are in same group as collapsible with `id`. Returns
   re-frame update map that hides the _other_ collapsibles."
  [id]
  (b/when-let [elem (.getElementById js/document id)
               group (.. elem -dataset -group)
               nodes-in-group (.querySelectorAll js/document (rfmt/format ".collapsible[data-group='%s']" group))
               ids (keep #(.-id %) nodes-in-group)
               update-map (into {} (mapv vector ids (repeat false)))]
    (dissoc update-map id)))

(rf/reg-event-fx
 ::set-expanded
 (fn [{:keys [db]} [_ id expanded?]]
   (cond-> {:db db}
     :always
     (assoc-in [:db ::id id] (true? expanded?))

     expanded?
     (update-in [:db ::id] merge (close-others-in-group id)))))

(defn- base-action
  "Base attributes for collapsible action."
  [{:keys [collapsible-id label] :as action}]
  (let [state @(rf/subscribe [::expanded collapsible-id])]
    (-> action
        (dissoc :collapsible-id :hide? :on-close :on-open :show?)
        (assoc :aria-controls collapsible-id
               :aria-expanded (if state
                                "true"
                                "false"))
        (assoc-some :label (cond label nil
                                 state (text :t.collapse/hide)
                                 :else (text :t.collapse/show))))))

(defn show-action
  "Action that shows collapsible on click. Use together with `action-link` or `action-button` atom."
  [{:keys [collapsible-id on-open] :as action}]
  (-> (base-action action)
      (assoc :on-click (fn [^js event]
                         (rf/dispatch [::set-expanded collapsible-id true])
                         (when on-open (on-open event))))))

(defn hide-action
  "Action that hides collapsible on click. Use together with `action-link` or `action-button` atom."
  [{:keys [collapsible-id on-close] :as action}]
  (-> (base-action action)
      (assoc :on-click (fn [^js event]
                         (rf/dispatch [::set-expanded collapsible-id false])
                         (when on-close (on-close event))))))

(defn toggle-action
  "Action that toggles collapsible state. Use together with `action-link` or `action-button` atom."
  [{:keys [collapsible-id on-close on-open] :as action}]
  (if @(rf/subscribe [::expanded collapsible-id])
    (hide-action action)
    (show-action action)))

(defn toggle-control
  "Action link that opens and/or hides collapsible. Sets focus on first input when opened, if exists.
   
   `action` is a map that supports following keys:
   - `:collapse-id` (required) string or keyword, id of controlled collapsible
   - `:on-close` function, invoked when collapsible is toggled hidden. false disables hide control 
   - `:on-open` function, invoked when collapsible is toggled open. false disables open control"
  [{:keys [on-close on-open] :as action}]
  (let [hide (not (false? on-close))
        open (not (false? on-open))]
    [:div.text-center
     (cond
       (and hide open) [atoms/action-link (toggle-action action)]
       hide [atoms/action-link (hide-action action)]
       open [atoms/action-link (show-action action)])]))


(defn- collapse-block [id {:keys [content-closed content-open]}]
  (if @(rf/subscribe [::expanded id])
    (when content-open [:div.collapse-open content-open])
    (when content-closed [:div.collapse-closed content-closed])))

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
  - `:class` string/keyword/vector, for wrapping element
  - `:group` string, only one collapsible in group can be open at a time
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area
  - `:top-less-button?` should top show less button be shown?"
  [{:keys [always bottom-less-button? class collapse collapse-hidden footer group id on-close on-open open? title top-less-button?]
    :or {bottom-less-button? true}}]
  (when collapse
    (assert id))
  (r/with-let [_ (rf/dispatch-sync [::set-expanded id open?])]
    [:div.collapsible.bordered-collapsible (assoc-some {:id id}
                                                       :class class
                                                       :data-group group)
     [:h2.card-header title]
     [:div.collapsible-contents
      always
      (when collapse
        [:<>
         (when top-less-button? [toggle-control {:collapsible-id id
                                                 :on-close on-close
                                                 :on-open false}])
         [collapse-block id {:content-closed collapse-hidden
                             :content-open collapse}]
         [toggle-control {:collapsible-id id
                          :on-close (if bottom-less-button?
                                      on-close
                                      false)
                          :on-open on-open}]])
      footer]]))

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
  - `:class` string/keyword/vector, for wrapping element
  - `:group` string, only one collapsible in group can be open at a time
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area"
  [{:keys [always class collapse collapse-hidden footer group id open? title]}]
  (when collapse
    (assert id))
  (r/with-let [_ (rf/dispatch-sync [::set-expanded id open?])]
    [:div.collapsible (assoc-some {:id id}
                                  :class class
                                  :data-group group)
     (when title [:h2.card-header title])
     [:div.collapsible-contents
      always
      [collapse-block id {:content-open collapse
                          :content-hidden collapse-hidden}]
      footer]]))

(defn expander
  "Collapsible variation where simple title is the toggle control.
  
  Pass a map of options with the following keys:
  - `:collapse` component that is toggled displayed or not
  - `:id` (required) unique id
  - `:class` string/keyword/vector, for wrapping element
  - `:group` string, only one collapsible in group can be open at a time
  - `:open?` should the collapsible be initially open?
  - `:title` component or text displayed in title area"
  [{:keys [class collapse id group on-close on-open open? title]}]
  (r/with-let [_ (rf/dispatch-sync [::set-expanded id open?])]
    (let [expanded? @(rf/subscribe [::expanded id])]
      [:div.collapsible.expander-collapsible (assoc-some {:id id}
                                                         :class class
                                                         :data-group group)
       [atoms/action-link (assoc (toggle-action {:collapsible-id id
                                                 :on-close on-close
                                                 :on-open on-open})
                                 :class "expander-toggle"
                                 :label [:div.d-flex.align-items-center.gap-1.pointer
                                         title
                                         [:div
                                          [:i.fa {:class (if expanded?
                                                           "fa-chevron-up"
                                                           "fa-chevron-down")}]]])]
       [:div.collapsible-contents
        [collapse-block id {:content-open collapse}]]])))

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
                        :always [:p "Collapsed input receives focus when opened"]
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
                                 [rems.collapsible/toggle-control {:collapsible-id "standard-collapsible-4"}]]
                        :collapse (into [:div] (repeat 3 [:p "I am long content that you can hide"]))
                        :collapse-hidden [:p "I am content that is only visible when collapsed"]
                        :footer [:div.solid-group "I am the footer that is always visible"]}])

   (component-info minimal)
   (example "default use"
            [minimal {:id "minimal-collapsible-1"
                      :always [rems.collapsible/toggle-control {:collapsible-id "minimal-collapsible-1"}]
                      :collapse [:p "I am content that you can hide"]}])
   (example "footer controls"
            [minimal {:id "minimal-collapsible-2"
                      :footer [rems.collapsible/toggle-control {:collapsible-id "minimal-collapsible-2"}]
                      :collapse [:p "I am content that you can hide"]}])
   (example "all options"
            [minimal {:id "minimal-collapsible-3"
                      :open? true
                      :always [rems.collapsible/toggle-control {:collapsible-id "minimal-collapsible-3"}]
                      :collapse (into [:div] (repeat 3 [:p "I am long content that you can hide"]))
                      :collapse-hidden [:p "I am content that is only visible when collapsed"]
                      :footer [rems.collapsible/toggle-control {:collapsible-id "minimal-collapsible-3"}]}])

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
