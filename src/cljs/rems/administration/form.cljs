(ns rems.administration.form
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! fetch post! vec-dissoc]]
            [rems.application :refer [enrich-user]]))

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

(defn- form-item-title-field []
  [:div.form-group.field
   [:label "Field title"]                                   ; TODO: translation
   [:div.form-group.row
    [:label.col-sm-1.col-form-label "EN"]
    [:div.col-sm-11
     [:input.form-control {:type "text"}]]]
   [:div.form-group.row
    [:label.col-sm-1.col-form-label "FI"]
    [:div.col-sm-11
     [:input.form-control {:type "text"}]]]])

(defn- form-item-type-field []
  [:div.form-group.field
   [:div.form-check
    [:input.form-check-input {:id "type-text"
                              :type "radio"
                              :name "type"
                              :value "text"}]
    [:label.form-check-label {:for "type-text"}
     "Text field"]]                                         ; TODO: translation
   [:div.form-check
    [:input.form-check-input {:id "type-texta"
                              :type "radio"
                              :name "type"
                              :value "texta"}]
    [:label.form-check-label {:for "type-texta"}
     "Text area"]]                                          ; TODO: translation
   [:div.form-check
    [:input.form-check-input {:id "type-date"
                              :type "radio"
                              :name "type"
                              :value "date"}]
    [:label.form-check-label {:for "type-date"}
     "Date field"]]])                                       ; TODO: translation

(defn- form-item-optional-checkbox []
  [:div.form-group.field
   [:div.form-check
    [:input.form-check-input {:id "optional"
                              :type "checkbox"
                              :name "optional"}]
    [:label.form-check-label {:for "optional"}
     "Optional"]]])                                         ; TODO: translation

(defn- form-item-input-prompt-field []
  [:div.form-group.field
   [:label "Input prompt"]                                  ; TODO: translation
   [:div.form-group.row
    [:label.col-sm-1.col-form-label "EN"]
    [:div.col-sm-11
     [:input.form-control {:type "text"}]]]
   [:div.form-group.row
    [:label.col-sm-1.col-form-label "FI"]
    [:div.col-sm-11
     [:input.form-control {:type "text"}]]]])

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
                         [form-item-title-field]
                         [form-item-type-field]
                         [form-item-optional-checkbox]
                         [form-item-input-prompt-field]]))

               [:div.form-item.new-form-item
                [add-form-item-button]]
               [:div.col.commands
                [cancel-button]
                [save-form-button]]]}]))
