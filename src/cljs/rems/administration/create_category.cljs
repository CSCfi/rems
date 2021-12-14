(ns rems.administration.create-category
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text localized]]
            [rems.util :refer [navigate! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (dissoc db ::form)
    :dispatch-n [[::categories]]}))

(fetcher/reg-fetcher ::categories "/api/categories")

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-categories (fn [db _] (get-in db [::form :categories])))
(rf/reg-event-db ::set-selected-categories (fn [db [_ categories]] (assoc-in db [::form :categories] categories)))

(defn- valid-request? [request]
  (not (str/blank? (:category/title request))))

(defn build-request [form]
  (let [request {:category/title (:title form)
                 :category/description (:description form)
                 :category/children (map #(select-keys % [:category/id]) (:categories form))}]
    (when (valid-request? request)
      request)))

(rf/reg-event-fx
 ::create-category
 (fn [_ [_ request]]
   (let [description [text :t.administration/save]]
     (post! "/api/categories"
            {:params request
             :handler (flash-message/default-success-handler
                       :top description #(navigate! (str "/administration/categories/" (:category/id %))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private categories-dropdown-id "categories-dropdown")

(defn- category-title-field []
  [localized-text-field context {:keys [:title]
                                 :label (text :t.administration/title)}])

(defn- category-description-field []
  [localized-text-field context {:keys [:description]
                                 :label (text :t.administration/description)}])

(defn- category-children-field []
  (let [categories @(rf/subscribe [::categories])
        selected-categories @(rf/subscribe [::selected-categories])
        item-selected? (set selected-categories)]
    [:div.form-group
     [:label.administration-field-label {:for categories-dropdown-id} (text :t.administration/category-children)]
     [dropdown/dropdown
      {:id categories-dropdown-id
       :items categories
       :multi? true
       :item-key :category/id
       :item-label #(localized (:category/title %))
       :item-selected? item-selected?
       :clearable? true
       :on-change #(rf/dispatch [::set-selected-categories %])}]]))

(defn- save-category-button [form]
  (let [request (build-request form)]
    [:button#save.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (rf/dispatch [::create-category request]))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/categories"
   (text :t.administration/cancel)])

(defn create-category-page []
  (let [loading? @(rf/subscribe [::categories :fetching?])
        form @(rf/subscribe [::form])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/create-category)]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-category"
       :title (text :t.administration/create-category)
       :always [:div
                (if loading?
                  [:div#category-loader [spinner/big]]
                  [:div#category-editor.fields
                   [category-title-field]
                   [category-description-field]
                   [category-children-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-category-button form]]])]}]]))