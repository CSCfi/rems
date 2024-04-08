(ns rems.administration.change-catalogue-item-form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
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
                                      (assoc item :id new-catalogue-item-id :formid (:form/id new-form) :form-name (:form/internal-name new-form))
                                      item))))))


(rf/reg-event-fx
 ::change-catalogue-item-form
 (fn [_ [_ catalogue-item-id form on-success]]
   (post! (str "/api/catalogue-items/" catalogue-item-id "/change-form")
          {:params {:form (:form/id form)}
           :handler (fn [result]
                      (rf/dispatch [::update-catalogue-item catalogue-item-id (:catalogue-item-id result) form])
                      (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} catalogue-item-id])
                      (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} (:catalogue-item-id result)])
                      (on-success))
           :error-handler (flash-message/default-error-handler :top [text :t.administration/change-form])})
   {}))

(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(rf/reg-fx ::fetch-forms fetch-forms)

(rf/reg-event-db ::fetch-forms-result (fn [db [_ forms]] (assoc db ::forms forms)))

(rf/reg-sub ::forms (fn [db _] (::forms db)))

(defn- form-change-loop [items form]
  (let [item (first items)]
    (cond (empty? items)
          (flash-message/show-default-success! :top [text :t.administration/change-form])

          (not= (:formid item) (:form/id form))
          (rf/dispatch [::change-catalogue-item-form (:id item) form #(form-change-loop (rest items) form)])

          :else (recur (rest items) form))))

(defn- all-items-have-the-form-already? [items form]
  (every? #(= (:form/id form) (:formid %)) items))

(defn- change-catalogue-item-form-button [items form]
  [:button.btn.btn-primary
   {:type :button
    :on-click (fn [] (form-change-loop items form))
    :disabled (or (empty? items)
                  (all-items-have-the-form-already? items form))}
   (text :t.administration/change)])

(rf/reg-sub
 ::catalogue-items-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue-items])
    (rf/subscribe [:language])])
 (fn [[catalogue language] _]
   (map (fn [item]
          {:key (:id item)
           :name (let [title (get-localized-title item language)]
                   {:value title
                    :display-value [atoms/link nil
                                    (str "/administration/catalogue-items/" (:id item))
                                    title]})
           :form (let [value (:form-name item)]
                   {:value value
                    :display-value (if value
                                     [atoms/link nil
                                      (str "/administration/forms/" (:formid item))
                                      value]
                                     [text :t.administration/no-form])})})
        catalogue)))

(defn catalogue-items-table []
  [:div
   [table/table {:id ::catalogue
                 :columns [{:key :name
                            :title (text :t.administration/catalogue-item)}
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
                         :item-label :form/internal-name
                         :item-selected? #(= (:form/id %) (:form/id form))
                         :clearable? true
                         :placeholder (text :t.administration/no-form)
                         :on-change on-change}]]))

(defn change-catalogue-item-form-page []
  ;; catalogue items must be setup in the previous page
  ;; it can be empty when we reload or relogin
  ;; then we can redirect back to the previous page
  (let [catalogue-items @(rf/subscribe [::catalogue-items])]
    (when (empty? catalogue-items)
      (navigate! "/administration/catalogue-items"))
    [:div
     [administration/navigator]
     [document-title (text :t.administration/change-form)]
     [flash-message/component :top]
     [:div
      [:p (text :t.change-form/change-form-intro)]
      [catalogue-items-table]
      [form-select]
      [:div.col.commands
       [administration/back-button "/administration/catalogue-items"]
       [change-catalogue-item-form-button catalogue-items @(rf/subscribe [::form])]]]]))
