(ns rems.administration.update-catalogue-item
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.table :as table]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [navigate! fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-items]]
   {:db (-> (assoc db
                   ::form {:form/id :do-not-change-form}
                   ::workflow {:id :do-not-change-workflow})
            (assoc ::catalogue-items catalogue-items))
    ::fetch-forms nil
    ::fetch-workflows nil}))

(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(defn- fetch-workflows []
  (fetch "/api/workflows"
         {:handler #(rf/dispatch [::fetch-workflows-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch workflows")}))

(rf/reg-fx ::fetch-forms fetch-forms)
(rf/reg-fx ::fetch-workflows fetch-workflows)
(rf/reg-event-db ::fetch-forms-result (fn [db [_ forms]] (assoc db ::forms forms)))
(rf/reg-sub ::forms (fn [db _] (::forms db)))
(rf/reg-event-db ::fetch-workflows-result (fn [db [_ workflows]] (assoc db ::workflows workflows)))
(rf/reg-sub ::workflows (fn [db _] (::workflows db)))

(rf/reg-sub ::catalogue-items (fn [db _] (::catalogue-items db)))
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form (fn [db [_ form]] (assoc-in db [::form] form)))
(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-event-db ::set-workflow (fn [db [_ workflow]] (assoc-in db [::workflow] workflow)))

(rf/reg-event-db
 ::replace-catalogue-item
 (fn [db [_ {:keys [old-catalogue-item-id new-catalogue-item-id new-form new-workflow]}]]
   (update db ::catalogue-items (fn [items]
                                  (for [item items]
                                    (if (= (:id item) old-catalogue-item-id)
                                      (assoc item
                                             :id new-catalogue-item-id
                                             :formid (:form/id new-form) :form-name (:form/internal-name new-form)
                                             :wfid (:wfid new-workflow)
                                             :workflow-name (:title new-workflow))
                                      item))))))


(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ {:keys [catalogue-item-id form workflow] :as params} on-success]]
   (post! (str "/api/catalogue-items/" catalogue-item-id "/update")
          {:params {:form (:form/id form)
                    :workflow (:id workflow)}
           :handler (fn [result]
                      (rf/dispatch [::replace-catalogue-item {:old-catalogue-item-id catalogue-item-id
                                                              :new-catalogue-item-id (:catalogue-item-id result)
                                                              :new-form form
                                                              :new-workflow workflow}])
                      (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} catalogue-item-id])
                      (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} (:catalogue-item-id result)])
                      (on-success))
           :error-handler (flash-message/default-error-handler :top [text :t.administration/update-catalogue-item])})
   {}))

(defn- item-update-loop [items form workflow]
  (let [item (first items)]
    (cond (empty? items)
          (flash-message/show-default-success! :top [text :t.administration/update-catalogue-item])

          (or (not= (:formid item) (:form/id form))
              (not= (:wfid item) (:id workflow)))
          (rf/dispatch [::update-catalogue-item
                        {:catalogue-item-id (:id item)
                         :form form
                         :workflow workflow}
                        #(item-update-loop (rest items) form workflow)])

          :else (recur (rest items) form workflow))))

(defn- all-items-have-the-form-and-workflow-already? [items form workflow]
  (every? #(and (= (:form/id form) (:formid %))
                (= (:id workflow) (:wfid %)))
          items))

(defn- no-change-selected? [form workflow]
  (and (= (:form/id form) :do-not-change-form)
       (= (:id workflow) :do-not-change-workflow)))

(defn- update-catalogue-item-button [items {:keys [form workflow]}]
  [:button.btn.btn-primary
   {:type :button
    :on-click (fn [] (item-update-loop items form workflow))
    :disabled (or (empty? items)
                  (all-items-have-the-form-and-workflow-already? items form workflow)
                  (no-change-selected? form workflow))}
   (text :t.administration/update-catalogue-item)])

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
                                     [text :t.administration/no-form])})
           :workflow (let [value (:workflow-name item)]
                       {:value value
                        :display-value [atoms/link nil
                                        (str "/administration/workflows/" (:wfid item))
                                        value]})})
        catalogue)))

(defn catalogue-items-table []
  [:div
   [table/table {:id ::catalogue
                 :columns [{:key :name
                            :title (text :t.administration/catalogue-item)}
                           {:key :form
                            :title (text :t.administration/form)}
                           {:key :workflow
                            :title (text :t.administration/workflow)}]
                 :rows [::catalogue-items-table-rows]
                 :default-sort-column :name}]])

(defn form-select []
  (let [form @(rf/subscribe [::form])
        on-change #(rf/dispatch [::set-form %])]
    [:div.form-group
     [:label {:for :form-dropdown} (text :t.update-catalogue-item/form-selection)]
     [dropdown/dropdown {:id :form-dropdown
                         :items (concat [{:form/id :do-not-change-form
                                          :form/internal-name (text :t.administration/do-not-change-form)}
                                         {:form/id nil
                                          :form/internal-name (text :t.administration/no-form)}]
                                        @(rf/subscribe [::forms]))
                         :item-key :form/id
                         :item-label :form/internal-name
                         :item-selected? #(= (:form/id %) (:form/id form))
                         :on-change on-change}]]))

(defn workflow-select []
  (let [workflow @(rf/subscribe [::workflow])
        on-change #(rf/dispatch [::set-workflow %])]
    [:div.form-group
     [:label {:for :workflow-dropdown} (text :t.update-catalogue-item/workflow-selection)]
     [dropdown/dropdown {:id :workflow-dropdown
                         :items (concat [{:id :do-not-change-workflow
                                          :title (text :t.administration/do-not-change-workflow)}]
                                        @(rf/subscribe [::workflows]))
                         :item-key :id
                         :item-label :title
                         :item-selected? #(= (:id %) (:id workflow))
                         :on-change on-change}]]))

(defn update-catalogue-item-page []
  ;; catalogue items must be setup in the previous page
  ;; it can be empty when we reload or relogin
  ;; then we can redirect back to the previous page
  (let [catalogue-items @(rf/subscribe [::catalogue-items])]
    (when (empty? catalogue-items)
      (navigate! "/administration/catalogue-items"))
    [:div
     [administration/navigator]
     [document-title (text :t.administration/update-catalogue-item)]
     [flash-message/component :top]
     [:div
      [:p (text :t.update-catalogue-item/update-catalogue-item-intro)]
      [catalogue-items-table]
      [form-select]
      [workflow-select]
      [:div.col.commands
       [administration/back-button "/administration/catalogue-items"]
       [update-catalogue-item-button catalogue-items
        {:form @(rf/subscribe [::form])
         :workflow @(rf/subscribe [::workflow])}]]]]))
