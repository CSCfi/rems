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


; available actors

(defn- fetch-form-items []
  (fetch "/api/forms/items" {:handler #(rf/dispatch [::fetch-form-items-result %])}))

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

(defn- add-form-item-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::set-form-field [:items (count (:items form))] {}])}
     "Add field"]))                                         ; TODO: translation

(defn- remove-form-item-button [round]
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-secondary
     {:on-click #(rf/dispatch [::set-form-field [:items] (vec-dissoc (:items form) round)])
      :style {:float "right"}}
     "Remove field"]))                                      ; TODO: translation

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
                        [:div
                         {:key item}
                         [remove-form-item-button item]
                         ; TODO
                         [:p (str "Field " (inc item))]]))

               [:div.col.commands
                [add-form-item-button]
                [cancel-button]
                [save-form-button]]]}]))
