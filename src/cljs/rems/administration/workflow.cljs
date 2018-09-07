(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.components :refer [radio-button-group text-field]]
            [rems.application :refer [enrich-user]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! fetch post! vec-dissoc]]))

(defn- reset-form [db]
  (assoc db ::form {:rounds []}))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   ; TODO: loading indicator
   {:db (reset-form db)
    ::fetch-actors nil}))


; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))


; form submit

(defn- valid-request? [request]
  (and (not (str/blank? (:prefix request)))
       (not (str/blank? (:title request)))
       (every? (fn [round]
                 (and (not (nil? (:type round)))
                      (not (empty? (:actors round)))))
               (:rounds request))))

(defn- build-actor-request [actor]
  {:userid (:userid actor)})

(defn- build-round-request [round]
  {:type (:type round)
   :actors (map build-actor-request (:actors round))})

(defn build-request [form]
  (let [request {:prefix (:prefix form)
                 :title (:title form)
                 :rounds (map build-round-request (:rounds form))}]
    (when (valid-request? request)
      request)))

(defn- create-workflow [request]
  (post! "/api/workflows/create" {:params request
                                  ; TODO: error handling
                                  :handler (fn [resp] (dispatch! "#/administration"))}))

(rf/reg-event-fx
 ::create-workflow
 (fn [_ [_ request]]
   (create-workflow request)
   {}))


; selected actors

(defn- remove-actor [actors actor]
  (filter #(not= (:userid %)
                 (:userid actor))
          actors))

(rf/reg-event-db
 ::remove-actor
 (fn [db [_ round actor]]
   (update-in db [::form :rounds round :actors] remove-actor actor)))

(defn- add-actor [actors actor]
  (-> actors
      (remove-actor actor) ; avoid duplicates
      (conj actor)))

(rf/reg-event-db
 ::add-actor
 (fn [db [_ round actor]]
   (update-in db [::form :rounds round :actors] add-actor actor)))


; available actors

(defn- fetch-actors []
  (fetch "/api/workflows/actors" {:handler #(rf/dispatch [::fetch-actors-result %])}))

(rf/reg-fx
 ::fetch-actors
 (fn [_]
   (fetch-actors)))

(rf/reg-event-db
 ::fetch-actors-result
 (fn [db [_ actors]]
   (assoc db ::actors (map enrich-user actors))))

(rf/reg-sub
 ::actors
 (fn [db _]
   (::actors db)))


;;;; UI ;;;;

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- workflow-prefix-field []
  [text-field context {:keys [:prefix]
                       :label (text :t.create-resource/prefix) ; TODO: extract common translation
                       :placeholder (text :t.create-resource/prefix-placeholder)}]) ; TODO: extract common translation

(defn- workflow-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(defn- round-type-radio-group [round]
  [radio-button-group context {:keys [:rounds round :type]
                               :orientation :horizontal
                               :options [{:value :approval
                                          :label (text :t.create-workflow/approval-round)}
                                         {:value :review
                                          :label (text :t.create-workflow/review-round)}]}])

(defn- workflow-actors-field [round]
  (let [form @(rf/subscribe [::form])
        round-type (get-in form [:rounds round :type])
        all-actors @(rf/subscribe [::actors])
        selected-actors (get-in form [:rounds round :actors])]
    (when round-type
      [:div.form-group
       [:label (case round-type
                 :approval (text :t.create-workflow/approvers)
                 :review (text :t.create-workflow/reviewers))]
       [autocomplete/component
        {:value (sort-by :userid selected-actors)
         :items all-actors
         :value->text #(:display %2)
         :item->key :userid
         :item->text :display
         :item->value identity
         :search-fields [:display :userid]
         :add-fn #(rf/dispatch [::add-actor round %])
         :remove-fn #(rf/dispatch [::remove-actor round %])}]])))

(defn- next-workflow-arrow []
  [:i.next-workflow-arrow.fa.fa-long-arrow-alt-down
   {:aria-hidden true}])

(defn- add-round-button []
  (let [form @(rf/subscribe [::form])]
    [:a
     {:href "#"
      :on-click (fn [event]
                  (.preventDefault event)
                  ; TODO: refactor to re-frame events
                  (rf/dispatch [::set-form-field [:rounds (count (:rounds form))] {}]))}
     (text :t.create-workflow/add-round)]))

(defn- remove-round-button [round]
  (let [form @(rf/subscribe [::form])]
    [:a.remove-workflow-round
     {:href "#"
      :on-click (fn [event]
                  (.preventDefault event)
                  ; TODO: refactor to re-frame events
                  (rf/dispatch [::set-form-field [:rounds] (vec-dissoc (:rounds form) round)]))
      :aria-label (text :t.create-workflow/remove-round)
      :title (text :t.create-workflow/remove-round)}
     [:i.icon-link.fas.fa-times
      {:aria-hidden true}]]))

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])
        request (build-request form)]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-workflow request])
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])
        num-rounds (count (:rounds form))
        last-round (dec num-rounds)]
    [collapsible/component
     {:id "create-workflow"
      :title (text :t.administration/create-workflow)
      :always [:div
               [workflow-prefix-field]
               [workflow-title-field]

               (doall (for [round (range num-rounds)]
                        [:div.workflow-round
                         {:key round}
                         [remove-round-button round]

                         [:h2 (text-format :t.create-workflow/round-n (inc round))]
                         [round-type-radio-group round]
                         [workflow-actors-field round]
                         (when (not= round last-round)
                           [next-workflow-arrow])]))

               [:div.workflow-round.new-workflow-round
                [add-round-button]]

               [:div.col.commands
                [cancel-button]
                [save-workflow-button]]]}]))
