(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! fetch post!]]
            [rems.autocomplete :as autocomplete]
            [rems.application :refer [enrich-user]]))

(defn- valid-request? [request]
  (and (not (str/blank? (:prefix request)))
       (not (str/blank? (:title request)))
       (not (empty? (:rounds request)))
       (every? (fn [round]
                 (and (not (nil? (:type round)))
                      (not (empty? (:actors round)))))
               (:rounds request))))

(defn build-actor-request [actor]
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

(defn- create-workflow [form]
  (post! "/api/workflows/create" {:params (build-request form)
                                  :handler (fn [resp]
                                             (dispatch! "#/administration"))}))

(rf/reg-event-fx
  ::create-workflow
  (fn [_ [_ form]]
    (create-workflow form)
    {}))

(rf/reg-event-db
  ::reset-create-workflow
  (fn [db _]
    (assoc db ::form {:rounds [{}]})))

(rf/reg-sub
  ::form
  (fn [db _]
    (::form db)))

(rf/reg-event-db
  ::set-form-field
  (fn [db [_ keys value]]
    (assoc-in db (concat [::form] keys) value)))


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
      (remove-actor actor)                                  ; avoid duplicates
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

(rf/reg-event-fx
  ::start-fetch-actors
  (fn [{:keys [db]}]
    {:db (assoc db ::loading? true)
     ::fetch-actors []}))

(rf/reg-event-db
  ::fetch-actors-result
  (fn [db [_ actors]]
    (-> db
        (assoc ::actors (map enrich-user actors))
        (dissoc ::loading?))))

(rf/reg-sub
  ::actors
  (fn [db _]
    (::actors db)))


;;;; UI ;;;;

(defn- workflow-prefix-field []
  (let [form @(rf/subscribe [::form])
        keys [:prefix]
        id "prefix"]
    [:div.form-group.field
     [:label {:for id} (text :t.create-resource/prefix)]    ; TODO: extract common translation
     [:input.form-control {:type :text
                           :id id
                           :placeholder (text :t.create-resource/prefix-placeholder) ; TODO: extract common translation
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- workflow-title-field []
  (let [form @(rf/subscribe [::form])
        keys [:title]
        id "title"]
    [:div.form-group.field
     [:label {:for id} (text :t.create-workflow/title)]
     [:input.form-control {:type "text"
                           :id id
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- round-type-radio-button [round value label]
  (let [form @(rf/subscribe [::form])
        keys [:rounds round :type]
        id (str "round-" round "-type-" (name value))]
    [:div.form-check.form-check-inline
     [:input.form-check-input {:type "radio"
                               :id id
                               :value (name value)
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [::set-form-field keys value]))}]
     [:label.form-check-label {:for id} label]]))

(defn- round-type-radio-group [round]
  [:div.form-group.field
   [round-type-radio-button round :approval (text :t.create-workflow/approval-round)]
   [round-type-radio-button round :review (text :t.create-workflow/review-round)]])

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

(defn- add-round-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::set-form-field [:rounds (count (:rounds form))] {}])}
     (text :t.create-workflow/add-round)]))

(defn vec-dissoc [coll index]
  (vec (concat (subvec coll 0 index)
               (subvec coll (inc index)))))

(defn- remove-round-button [round]
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-secondary
     {:on-click #(rf/dispatch [::set-form-field [:rounds] (vec-dissoc (:rounds form) round)])
      :style {:float "right"}}
     (text :t.create-workflow/remove-round)]))

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-workflow form])
      :disabled (not (build-request form))}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])]
    [collapsible/component
     {:id "create-workflow"
      :title (text :t.administration/create-workflow)
      :always [:div
               [workflow-prefix-field]
               [workflow-title-field]
               (doall (for [round (range (count (:rounds form)))]
                        [:div
                         {:key round}
                         [remove-round-button round]
                         [:h2 (text-format :t.create-workflow/round-n (inc round))]
                         [round-type-radio-group round]
                         [workflow-actors-field round]]))

               [:div.col.commands
                [add-round-button]
                [cancel-button]
                [save-workflow-button]]]}]))
