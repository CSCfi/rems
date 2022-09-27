(ns rems.administration.edit-category
  (:require [clojure.string :as str]
            [medley.core :refer [assoc-some]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field number-field]]
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
               :display-order (:category/display-order category)
               :categories (:category/children category)}}))))

(fetcher/reg-fetcher ::categories "/api/categories")
(fetcher/reg-fetcher ::category "/api/categories/:id" {:path-params (fn [db] {:id (::category-id db)})
                                                       :on-success #(rf/dispatch [::update-loading!])})

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
 ::edit-category
 (fn [{:keys [db]} [_ request]]
   (let [description [text :t.administration/save]
         category-id (parse-int (::category-id db))]
     (put! (str "/api/categories/edit")
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
                       :top description #(navigate! "/administration/categories"))
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
  (let [category-id (:category/id @(rf/subscribe [::category]))
        categories (->> @(rf/subscribe [::categories])
                        (filter #(not= category-id (:category/id %))))
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
                  (rf/dispatch [::edit-category request]))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- delete-category-button []
  [:button#delete.btn.btn-primary
   {:type :button
    :on-click #(when (js/confirm (text :t.administration/delete-confirmation))
                 (rf/dispatch [::delete-category]))}
   (text :t.administration/delete)])

(defn- cancel-button []
  (let [category (rf/subscribe [::category])]
    [atoms/link {:class "btn btn-secondary" :id :cancel}
     (str "/administration/categories/" (:category/id @category))
     (text :t.administration/cancel)]))

(defn edit-category-page []
  (let [loading? @(rf/subscribe [::categories :fetching?])
        form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])]
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
                   [category-display-order-field]
                   [category-children-field]

                   [:div.col.commands
                    [cancel-button]
                    [delete-category-button]
                    [save-category-button form languages]]])]}]]))
