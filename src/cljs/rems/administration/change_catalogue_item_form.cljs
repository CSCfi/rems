(ns rems.administration.change-catalogue-item-form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [navigate! fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-items]]
   {:db (-> (dissoc db ::form)
            (assoc ::catalogue-items catalogue-items))
    ::fetch-forms nil}))

(rf/reg-sub ::catalogue-items (fn [db _] (::catalogue-items db)))
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form (fn [db [_ form]] (assoc-in db [::form] form)))

(rf/reg-event-db
 ::update-catalogue-item
 (fn [db [_ old-catalogue-item-id new-catalogue-item-id new-form]]
   (update db ::catalogue-items (fn [items]
                                  (for [item items]
                                    (if (= (:id item) old-catalogue-item-id)
                                      (assoc item :id new-catalogue-item-id :formid (:form/id new-form) :form-name (:form/title new-form))
                                      item))))))

(defn- change-catalogue-item-form! [{:keys [db]} [_ catalogue-item-id form on-success]]
  (let [description [text :t.administration/change-form]]
    (post! (str  "/api/catalogue-items/" catalogue-item-id "/change-form")
           {:params {:form (:form/id form)}
            :handler (fn [result]
                       (rf/dispatch [::update-catalogue-item catalogue-item-id (:catalogue-item-id result) form])
                       (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} catalogue-item-id])
                       (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} (:catalogue-item-id result)])
                       (on-success))
            :error-handler (flash-message/default-error-handler :top description)}))
  {})

(rf/reg-event-fx ::change-catalogue-item-form! change-catalogue-item-form!)

(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(rf/reg-fx ::fetch-forms fetch-forms)

(rf/reg-event-db ::fetch-forms-result (fn [db [_ forms]] (assoc db ::forms forms)))

(rf/reg-sub ::forms (fn [db _] (::forms db)))

(defn- form-change-loop [items form]
  (if (empty? items)
    (flash-message/show-default-success! :top [text :t.administration/change-form])
    (rf/dispatch [::change-catalogue-item-form! (:id (first items)) form (partial form-change-loop (rest items) form)])))

(defn- change-catalogue-item-form-button [items form]
  [:button.btn.btn-primary
   {:type :button
    :on-click (fn [] (form-change-loop items form))
    :disabled (or (nil? (:form/id form)) (empty? items))}
   (text :t.administration/change)])

(defn- to-catalogue-item [catalogue-item-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/catalogue-items/" catalogue-item-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::catalogue-items-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue-items])
    (rf/subscribe [:language])])
 (fn [[catalogue language] _]
   (map (fn [item]
          {:key (:id item)
           :name {:value (get-localized-title item language)}
           :form (let [value (:form-name item)]
                   {:value value
                    :td [:td.form
                         [atoms/link nil
                          (str "/administration/forms/" (:formid item))
                          value]]})})
        catalogue)))

(defn catalogue-items-table []
  [:div
   [table/table {:id ::catalogue
                 :columns [{:key :name
                            :title (text :t.catalogue/header)}
                           {:key :form
                            :title (text :t.administration/form)}]
                 :rows [::catalogue-items-table-rows]
                 :default-sort-column :name}]])

(defn form-select []
  (let [form @(rf/subscribe [::form])
        on-change #(rf/dispatch [::set-form %])]
    [:div.form-group
     [:label {:for :form-dropdown} (text :t.change-form/form-selection)]
     [dropdown/dropdown {:id :form-dropdown
                         :items @(rf/subscribe [::forms])
                         :item-key :form/id
                         :item-label :form/title
                         :item-selected? #(= (:form/id %) (:form/id form))
                         :on-change on-change}]]))

(defn change-catalogue-item-form-page []
  ;; catalogue items must be setup in the previous page
  ;; it can be empty when we reload or relogin
  ;; then we can redirect back to the previous page
  (let [catalogue-items @(rf/subscribe [::catalogue-items])]
    (when (empty? catalogue-items)
      (navigate! "/administration/catalogue-items"))
    [:div
     [administration-navigator-container]
     [document-title (text :t.administration/change-form)]
     [flash-message/component :top]
     [:div
      [:p (text :t.administration/change-form-intro)]
      [catalogue-items-table]
      [form-select]
      [:div.col.commands
       [change-catalogue-item-form-button catalogue-items @(rf/subscribe [::form])]]]]))
