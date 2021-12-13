(ns rems.administration.edit-category
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [parse-int]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text localized]]
            [rems.util :refer [navigate! put! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ category-id]]
   {:db (-> (dissoc db ::form)
            (assoc ::category-id category-id))
    :dispatch-n [[::categories]
                 (when category-id [::category])]}))

(rf/reg-event-db
 ::update-loading!
 (fn [db _]
   (merge
    db
    (when-let [category (get-in db [::category :data])]
      {::form {:title (:category/title category)
               :description (:category/description category)
               :categories (:category/children category)}}))))

(fetcher/reg-fetcher ::categories "/api/categories")
(fetcher/reg-fetcher ::category "/api/categories/:id" {:path-params (fn [db] {:id (::category-id db)})
                                                       :on-success #(rf/dispatch [::update-loading!])})

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
 ::edit-category
 (fn [{:keys [db]} [_ request]]
   (let [description [text :t.administration/save]
         category-id (parse-int (::category-id db))]
     (put! (str "/api/categories")
           {:params (assoc request :category/id category-id)
            :handler (flash-message/status-update-handler
                      :top description #(navigate! (str "/administration/categories/" category-id)))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::delete-category
 (fn [{:keys [db]} _]
   (let [description [text :t.administration/delete]
         category-id (parse-int (::category-id db))]
     (post! (str "/api/categories/delete")
            {:params {:category/id category-id}
             :handler (flash-message/status-update-handler
                       :top description #(navigate! "/administration/categories/"))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private categories-dropdown-id "categories-dropdown")

(defn- category-title-field []
  [localized-text-field context {:keys [:title]
                                 :label (text :t.administration/category-title)}])

(defn- category-description-field []
  [localized-text-field context {:keys [:description]
                                 :label (text :t.administration/category-description)}])

(defn- category-children-field []
  (let [category-id (:category/id @(rf/subscribe [::category]))
        categories (->> @(rf/subscribe [::categories])
                        (filter #(not= category-id (:category/id %))))
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
                  (rf/dispatch [::edit-category request]))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- delete-category-button []
  [:button#delete.btn.btn-primary
   {:type :button
    :on-click #(when (js/confirm (text :t.administration/delete-category))
                 (rf/dispatch [::delete-category]))}
   (text :t.administration/delete)])

(defn- cancel-button []
  (let [category (rf/subscribe [::category])]
    [atoms/link {:class "btn btn-secondary"}
     (str "/administration/categories/" (:category/id @category))
     (text :t.administration/cancel)]))

(defn edit-category-page []
  (let [loading? @(rf/subscribe [::categories :fetching?])
        form @(rf/subscribe [::form])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/edit-category)]
     [flash-message/component :top]
     [collapsible/component
      {:id "edit-category"
       :title (localized (:title form))
       :always [:div
                (if loading?
                  [:div#category-loader [spinner/big]]
                  [:div#category-editor.fields
                   [category-title-field]
                   [category-description-field]
                   [category-children-field]

                   [:div.col.commands
                    [cancel-button]
                    [delete-category-button]
                    [save-category-button form]]])]}]]))