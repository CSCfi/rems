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


;;;; UI ;;;;

(defn- form-prefix-field []
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

; TODO: extract text field component
(defn- form-title-field []
  (let [form @(rf/subscribe [::form])
        keys [:title]
        id "title"]
    [:div.form-group.field
     [:label {:for id} "Title"]                             ; TODO: translation
     [:input.form-control {:type "text"
                           :id id
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]))

(defn- form-item-title-localized-field [item lang]
  (let [form @(rf/subscribe [::form])
        keys [:items item :title lang]
        id (str "items-" item "-title-" lang)]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (s/upper-case lang)]
     [:div.col-sm-11
      [:input.form-control {:type "text"
                            :id id
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]]))

(defn- form-item-title-field [item]
  [:div.form-group.field
   [:label "Field title"]                                   ; TODO: translation
   [form-item-title-localized-field item "en"]
   [form-item-title-localized-field item "fi"]])

; TODO: extract radio button component
(defn- form-item-type-radio-button [item value label]
  (let [form @(rf/subscribe [::form])
        keys [:items item :type]
        name (str "item-" item "-type")
        id (str "item-" item "-type-" value)]
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

(defn- form-item-type-radio-group [item]
  [:div.form-group.field
   [form-item-type-radio-button item "text" "Text field"]   ; TODO: translation
   [form-item-type-radio-button item "texta" "Text area"]   ; TODO: translation
   [form-item-type-radio-button item "date" "Date field"]]) ; TODO: translation

; TODO: extract checkbox component
(defn- form-item-optional-checkbox [item]
  (let [form @(rf/subscribe [::form])
        keys [:items item :optional]
        id (str "item-" item "-optional")]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (boolean (get-in form keys))
                                :on-change #(rf/dispatch [::set-form-field keys (.. % -target -checked)])}]
      [:label.form-check-label {:for id}
       "Optional"]]]))                                      ; TODO: translation

; TODO: extract localized text field component
(defn- form-item-input-prompt-localized-field [item lang]
  (let [form @(rf/subscribe [::form])
        keys [:items item :input-prompt lang]
        id (str "items-" item "-input-prompt-" lang)]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (s/upper-case lang)]
     [:div.col-sm-11
      [:input.form-control {:type "text"
                            :id id
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [::set-form-field keys (.. % -target -value)])}]]]))

(defn- form-item-input-prompt-field [item]
  [:div.form-group.field
   [:label "Input prompt"]                                  ; TODO: translation
   [form-item-input-prompt-localized-field item "en"]
   [form-item-input-prompt-localized-field item "fi"]])

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
