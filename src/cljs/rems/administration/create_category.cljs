(ns rems.administration.create-category
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [medley.core :refer [assoc-some]]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field number-field]]
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

(defn- valid-localization? [text]
  (not (str/blank? text)))

(defn- valid-request? [request languages]
  (and (= (set languages)
          (set (keys (:category/title request))))
       (every? valid-localization? (vals (:category/title request)))
       (if-some [description (:category/description request)]
         (and (= (set languages)
                 (set (keys description)))
              (every? valid-localization? (vals description)))
         true)))

(defn- empty-map-is-nil [m]
  (if (every? str/blank? (vals m))
    nil
    m))

(defn build-request [form languages]
  (let [request (-> {:category/title (:title form)}
                    (assoc-some :category/description (empty-map-is-nil (:description form)))
                    (assoc-some :category/display-order (:display-order form))
                    (assoc-some :category/children (seq (map #(select-keys % [:category/id])
                                                             (:categories form)))))]
    (when (valid-request? request languages)
      request)))

(rf/reg-event-fx
 ::create-category
 (fn [_ [_ request]]
   (let [description [text :t.administration/save]]
     (post! "/api/categories/create"
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
                                 :label (str (text :t.administration/description) " "
                                             (text :t.administration/optional))}])

(defn- category-display-order-field []
  [number-field context {:keys [:display-order]
                         :label (str (text :t.administration/display-order) " "
                                     (text :t.administration/optional))}])

(defn- category-children-field []
  (let [categories @(rf/subscribe [::categories])
        selected-categories @(rf/subscribe [::selected-categories])
        item-selected? (set selected-categories)]
    [:div.form-group
     [:label.administration-field-label {:for categories-dropdown-id}
      (str (text :t.administration/category-children) " " (text :t.administration/optional))]
     [dropdown/dropdown
      {:id categories-dropdown-id
       :items categories
       :multi? true
       :item-key :category/id
       :item-label #(localized (:category/title %))
       :item-selected? item-selected?
       :clearable? true
       :on-change #(rf/dispatch [::set-selected-categories %])}]]))

(defn- save-category-button [form languages]
  (let [request (build-request form languages)]
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
        form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])]
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
                   [category-display-order-field]
                   [category-children-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-category-button form languages]]])]}]]))
