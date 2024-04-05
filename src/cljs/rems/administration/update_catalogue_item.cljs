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

;;; Form selection

(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(rf/reg-fx ::fetch-forms fetch-forms)
(rf/reg-event-db ::fetch-forms-result (fn [db [_ forms]] (assoc db ::forms forms)))
(rf/reg-sub ::forms (fn [db _] (::forms db)))
(rf/reg-sub ::forms-items
            :<- [::forms]
            (fn [forms]
              (concat [{:form/id :do-not-change-form
                        :form/internal-name (text :t.administration/do-not-change-form)}
                       {:form/id nil
                        :form/internal-name (text :t.administration/no-form)}]
                      forms)))

;;; Workflow selection

(defn- fetch-workflows []
  (fetch "/api/workflows"
         {:handler #(rf/dispatch [::fetch-workflows-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch workflows")}))

(rf/reg-fx ::fetch-workflows fetch-workflows)
(rf/reg-event-db ::fetch-workflows-result (fn [db [_ workflows]] (assoc db ::workflows workflows)))
(rf/reg-sub ::workflows (fn [db _] (::workflows db)))
(rf/reg-sub ::workflows-items
            :<- [::workflows]
            (fn [workflows]
              (concat [{:id :do-not-change-workflow
                        :title (text :t.administration/do-not-change-workflow)}]
                      workflows)))

;;; Catalogue items
(rf/reg-sub ::catalogue-items (fn [db _] (::catalogue-items db)))

(rf/reg-event-db
 ::replace-catalogue-item
 (fn [db [_ {:keys [old-catalogue-item-id new-catalogue-item-id new-form new-workflow]}]]
   (update db ::catalogue-items (fn [items]
                                  (for [item items]
                                    (if (= (:id item) old-catalogue-item-id)
                                      (assoc item ; update the item with new data
                                             :id new-catalogue-item-id
                                             :formid (:form/id new-form)
                                             :form-name (:form/internal-name new-form)
                                             :wfid (:id new-workflow)
                                             :workflow-name (:title new-workflow))
                                      item))))))


(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ {:keys [catalogue-item new-form new-workflow old-form old-workflow]} on-success]]
   (let [old-catalogue-item-id (:id catalogue-item)]
     (post! (str "/api/catalogue-items/" old-catalogue-item-id "/update")
            {:params (merge (when-not (= :do-not-change-form (:form/id new-form))
                              {:form (:form/id new-form)})
                            (when-not (= :do-not-change-workflow (:id new-workflow))
                              {:workflow (:id new-workflow)}))
             :handler (fn [result]
                        (let [new-catalogue-item-id (:catalogue-item-id result)]
                          (when-not (= old-catalogue-item-id new-catalogue-item-id)
                            (rf/dispatch [::replace-catalogue-item {:old-catalogue-item-id old-catalogue-item-id
                                                                    :new-catalogue-item-id new-catalogue-item-id
                                                                    :new-form (if (= :do-not-change-form (:form/id new-form)) old-form new-form)
                                                                    :new-workflow (if (= :do-not-change-workflow (:id new-workflow)) old-workflow new-workflow)}])
                            (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} old-catalogue-item-id])
                            (rf/dispatch [:rems.table/toggle-row-selection {:id :rems.administration.catalogue-items/catalogue} new-catalogue-item-id]))
                          (on-success)))
             :error-handler (flash-message/default-error-handler :top [text :t.administration/update-catalogue-item])}))
   {}))


;;; Chosen update parameters

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form (fn [db [_ form]] (assoc-in db [::form] form)))
(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-event-db ::set-workflow (fn [db [_ workflow]] (assoc-in db [::workflow] workflow)))






;;; Logic

(defn- item-update-loop [items form workflow]
  (let [item (first items)]
    (cond (empty? items)
          (flash-message/show-default-success! :top [text :t.administration/update-catalogue-item])

          (or (not= :do-not-change-form (:form/id form))
              (not= :do-not-change-workflow (:id workflow))
              (not= (:formid item) (:form/id form))
              (not= (:wfid item) (:id workflow)))
          (rf/dispatch [::update-catalogue-item
                        {:catalogue-item item
                         :new-form form
                         :new-workflow workflow
                         :old-form {:form/id (:formid item)
                                    :form/internal-name (:form-name item)}
                         :old-workflow {:id (:wfid item)
                                        :title (:workflow-name item)}}
                        #(item-update-loop (rest items) form workflow)])

          :else (recur (rest items) form workflow))))

(defn- all-items-have-the-form-and-workflow-already? [items form workflow]
  (every? (fn [item]
            ;; TODO: unify the different attributes (:formid -> :form/id etc.)
            (and (or (= :do-not-change-form (:form/id form))
                     (= (:form/id form) (:formid item)))
                 (or (= :do-not-change-workflow (:id workflow))
                     (= (:id workflow) (:wfid item)))))
          items))

(defn- update-catalogue-item-button [items {:keys [form workflow]}]
[:button.btn.btn-primary
 {:type :button
  :on-click (fn [] (item-update-loop items form workflow))
  :disabled (or (empty? items)
                (all-items-have-the-form-and-workflow-already? items form workflow))}
 (text :t.administration/update-catalogue-item)])





;;; Input and result table

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
                         :items @(rf/subscribe [::forms-items])
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
                         :items @(rf/subscribe [::workflows-items])
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
