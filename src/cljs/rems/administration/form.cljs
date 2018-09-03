(ns rems.administration.form
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! fetch post! vec-dissoc]]
            [rems.application :refer [enrich-user]]
            [clojure.string :as s]))

(defn- uses-input-prompt? [form item]
  (let [form-item-type (get-in form [:items item :type])]
    (contains? #{"text" "texta"} form-item-type)))

(defn- valid-request? [request]
  ; TODO
  false)

(defn build-request [form]
  ; TODO
  (let [request {:prefix (:prefix form)
                 :title (:title form)}]
    (when (valid-request? request)
      request)))

(defn- create-form [form]
  (post! "/api/forms/create" {:params (build-request form)
                              :handler (fn [resp]
                                         (dispatch! "#/administration"))}))

(rf/reg-event-fx
  ::create-form
  (fn [_ [_ form]]
    (create-form form)
    {}))

(rf/reg-event-db
  ::reset-create-form
  (fn [db _]
    (assoc db ::form {:items []})))

(rf/reg-sub
  ::form
  (fn [db _]
    (::form db)))

(rf/reg-event-db
  ::set-form-field
  (fn [db [_ keys value]]
    (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db
  ::add-form-item
  (fn [db [_]]
    (assoc-in db [::form :items (count (:items (::form db)))] {})))

(rf/reg-event-db
  ::remove-form-item
  (fn [db [_ index]]
    (assoc-in db [::form :items] (vec-dissoc (:items (::form db)) index))))

(rf/reg-event-db
  ::move-form-item-up
  (fn [db [_ index]]
    (let [other (max 0 (dec index))]
      (-> db
          (assoc-in [::form :items index] (get-in db [::form :items other]))
          (assoc-in [::form :items other] (get-in db [::form :items index]))))))

(rf/reg-event-db
  ::move-form-item-down
  (fn [db [_ index]]
    (let [last-index (dec (count (get-in db [::form :items])))
          other (min last-index (inc index))]
      (-> db
          (assoc-in [::form :items index] (get-in db [::form :items other]))
          (assoc-in [::form :items other] (get-in db [::form :items index]))))))


; form items

(defn- fetch-form-items []
  (fetch "/api/form-items" {:handler #(rf/dispatch [::fetch-form-items-result %])}))

(rf/reg-fx
  ::fetch-form-items
  (fn [_]
    (fetch-form-items)))

(rf/reg-event-fx
  ::start-fetch-form-items
  (fn [{:keys [db]}]
    {:db (assoc db ::loading? true)
     ::fetch-form-items []}))

(rf/reg-event-db
  ::fetch-form-items-result
  (fn [db [_ items]]
    (-> db
        (assoc ::form-items items)
        (dissoc ::loading?))))

(rf/reg-sub
  ::form-items
  (fn [db _]
    (::form-items db)))


; reusable form components
; TODO: extract from this namespace

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn- keys-to-id [keys]
  (->> keys
       (map key-to-id)
       (str/join "-")))

(defn- text-field [{:keys [keys label placeholder]}]
  (let [form @(rf/subscribe [::form])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:label {:for id} label]
     [:input.form-control {:type "text"
                           :id id
                           :placeholder placeholder
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- localized-text-field-lang [{:keys [keys-prefix lang]}]
  (let [form @(rf/subscribe [::form])
        keys (conj keys-prefix lang)
        id (keys-to-id keys)]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (s/upper-case (name lang))]
     [:div.col-sm-11
      [:input.form-control {:type "text"
                            :id id
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]]))

(defn- localized-text-field [{:keys [keys label]}]
  (let [languages @(rf/subscribe [:languages])]
    (into [:div.form-group.field
           [:label label]]
          (for [lang languages]
            [localized-text-field-lang {:keys-prefix keys
                                        :lang lang}]))))

(defn- checkbox [{:keys [keys label]}]
  (let [form @(rf/subscribe [::form])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (boolean (get-in form keys))
                                :on-change #(rf/dispatch [::set-form-field keys (.. % -target -checked)])}]
      [:label.form-check-label {:for id}
       label]]]))

(defn- radio-button [{:keys [keys value label]}]
  (let [form @(rf/subscribe [::form])
        name (keys-to-id keys)
        id (keys-to-id (conj keys value))]
    [:div.form-check
     [:input.form-check-input {:id id
                               :type "radio"
                               :name name
                               :value value
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [::set-form-field keys value]))}]
     [:label.form-check-label {:for id}
      label]]))

(defn- radio-button-group [{:keys [keys options]}]
  (into [:div.form-group.field]
        (map (fn [{:keys [value label]}]
               [radio-button {:keys keys
                              :value value
                              :label label}])
             options)))

;;;; UI ;;;;

(defn- form-prefix-field []
  [text-field {:keys [:prefix]
               :label (text :t.create-resource/prefix)      ; TODO: extract common translation
               :placeholder (text :t.create-resource/prefix-placeholder)}]) ; TODO: extract common translation

(defn- form-title-field []
  [text-field {:keys [:title]
               :label "Title"}])                            ; TODO: translation

(defn- form-item-title-field [item]
  [localized-text-field {:keys [:items item :title]
                         :label "Field title"}])            ; TODO: translation

(defn- form-item-input-prompt-field [item]
  [localized-text-field {:keys [:items item :input-prompt]
                         :label "Input prompt"}])           ; TODO: translation

(defn- form-item-type-radio-group [item]
  [radio-button-group {:keys [:items item :type]
                       :options [{:value "text", :label "Text field"} ; TODO: translation
                                 {:value "texta", :label "Text area"} ; TODO: translation
                                 {:value "date", :label "Date field"}]}]) ; TODO: translation

(defn- form-item-optional-checkbox [item]
  [checkbox {:keys [:items item :optional]
             :label "Optional"}])                           ; TODO: translation

(defn- add-form-item-button []
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::add-form-item]))}
   "Add field"])                                            ; TODO: translation

(defn- remove-form-item-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::remove-form-item index]))
    :aria-label "Remove field"                              ; TODO: translation
    :title "Remove field"}
   [:i.icon-link.fas.fa-times
    {:aria-hidden true}]])

(defn- move-form-item-up-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::move-form-item-up index]))
    :aria-label "Move up"                                   ; TODO: translation
    :title "Move up"}
   [:i.icon-link.fas.fa-chevron-up
    {:aria-hidden true}]])

(defn- move-form-item-down-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::move-form-item-down index]))
    :aria-label "Move down"                                 ; TODO: translation
    :title "Move down"}
   [:i.icon-link.fas.fa-chevron-down
    {:aria-hidden true}]])

(defn- save-form-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-form form])
      :disabled (not (build-request form))}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-form-page []
  (let [form @(rf/subscribe [::form])]
    [collapsible/component
     {:id "create-form"
      :title (text :t.administration/create-form)
      :always [:div
               [form-prefix-field]
               [form-title-field]

               (doall (for [item (range (count (:items form)))]
                        [:div.form-item
                         {:key item}
                         [:div.form-item-controls
                          [move-form-item-up-button item]
                          [move-form-item-down-button item]
                          [remove-form-item-button item]]
                         [form-item-title-field item]
                         [form-item-optional-checkbox item]
                         [form-item-type-radio-group item]
                         (when (uses-input-prompt? form item)
                           [form-item-input-prompt-field item])]))

               [:div.form-item.new-form-item
                [add-form-item-button]]
               [:div.col.commands
                [cancel-button]
                [save-form-button]]]}]))
