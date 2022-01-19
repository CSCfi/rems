(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.tree :as tree]
            [rems.text :refer [text get-localized-title]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::catalogue ::draft-applications)
    :dispatch-n [[::full-catalogue]
                 [::full-catalogue-tree]
                 (when (roles/is-logged-in? (get-in db [:identity :roles])) [::draft-applications])
                 [:rems.table/reset]]}))

(fetcher/reg-fetcher ::full-catalogue "/api/catalogue")
(fetcher/reg-fetcher ::full-catalogue-tree "/api/catalogue/tree" {:result :roots})

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   (rf/subscribe [::full-catalogue]))
 (fn [catalogue _]
   (->> catalogue
        (filter :enabled)
        (remove :expired))))

(rf/reg-sub
 ::catalogue-tree
 (fn [_ _]
   (rf/subscribe [::full-catalogue-tree]))
 (fn [catalogue _]
   (->> catalogue
        (filter #(if (:category/id %)
                   true ; always pass categories
                   (:enabled %)))
        (remove #(if (:category/id %)
                   false ; always pass categories
                   (:expired %))))))

(defn- filter-drafts-only [applications]
  (filter form-fields-editable? applications))

(fetcher/reg-fetcher ::draft-applications "/api/my-applications" {:result filter-drafts-only})

;;;; UI

(defn- catalogue-item-more-info [item language config]
  (let [link (catalogue-item-more-info-url item language config)]
    (when link
      [:a.btn.btn-secondary
       {:href link
        :target :_blank
        :aria-label (str (text :t.catalogue/more-info)
                         ": "
                         (get-localized-title item language)
                         ", "
                         (text :t.link/opens-in-new-window))}
       [external-link] " " (text :t.catalogue/more-info)])))

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])
    (rf/subscribe [:logged-in])
    (rf/subscribe [:rems.cart/cart])
    (rf/subscribe [:rems.config/config])])
 (fn [[catalogue language logged-in? cart config] _]
   (let [cart-item-ids (set (map :id cart))]
     (map (fn [item]
            {:key (:id item)
             :name {:value (get-localized-title item language)}
             :commands {:td [:td.commands
                             [catalogue-item-more-info item language config]
                             (when logged-in?
                               (if (contains? cart-item-ids (:id item))
                                 [cart/remove-from-cart-button item language]
                                 [cart/add-to-cart-button item language]))]}})
          catalogue))))

(defn draft-application-list []
  (let [applications ::draft-applications]
    (when (seq @(rf/subscribe [applications]))
      [:div
       [:h2 (text :t.catalogue/continue-existing-application)]
       (text :t.catalogue/continue-existing-application-intro)
       [application-list/list
        {:id applications
         :applications applications
         :visible-columns #{:resource :last-activity :view}
         :default-sort-column :last-activity
         :default-sort-order :desc}]])))

(defn- catalogue-table []
  (let [catalogue {:id ::catalogue
                   :columns [{:key :name
                              :title (text :t.catalogue/header)}
                             {:key :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::catalogue-table-rows]
                   :default-sort-column :name}]
    [:div
     [table/search catalogue]
     [table/table catalogue]]))

(defn- catalogue-tree []
  (let [language @(rf/subscribe [:language])
        logged-in? @(rf/subscribe [:logged-in])
        cart @(rf/subscribe [:rems.cart/cart])
        cart-item-ids (set (map :id cart))
        config @(rf/subscribe [:rems.config/config])
        catalogue {:id ::catalogue-tree
                   :row-key #(or (some->> (:category/id %) (str "category_"))
                                 (:id %))
                   :show-matching-parents? (:catalogue-tree-show-matching-parents config)
                   :columns [{:key :name
                              :value #(or (get (:category/title %) language) (get-localized-title % language))
                              :sort-value #(vector (:category/display-order % 2147483647) ; can't use Java Integer/MAX_VALUE here but anything that is not set is last
                                                   (get (:category/title %) language "_") ; "_" means not set i.e. item is last
                                                   (get-localized-title % language))
                              :title (text :t.catalogue/header)
                              :content #(if (:category/id %)
                                          [:div.my-2
                                           [:h3.mb-0 {:class (str "fs-depth-" (:depth % 0))}
                                            (get (:category/title %) language)]
                                           (when-let [description (get (:category/description %) language)]
                                             [:div.mt-3 description])]
                                          [:div (get-localized-title % language)])
                              :col-span #(if (:category/id %) 2 1)}
                             {:key :commands
                              :content #(when-not (:category/id %)
                                          [:div.commands.w-100
                                           [catalogue-item-more-info % language config]
                                           (when logged-in?
                                             (if (contains? cart-item-ids (:id %))
                                               [cart/remove-from-cart-button % language]
                                               [cart/add-to-cart-button % language]))])
                              :sortable? false
                              :filterable? false}]
                   :children #(concat (:category/items %) (:category/children %))
                   :rows [::catalogue-tree]
                   :default-sort-column :name}]
    [:div
     [tree/search catalogue]
     [tree/tree catalogue]]))

(defn catalogue-page []
  (let [config @(rf/subscribe [:rems.config/config])]
    [:div
     [document-title (text :t.catalogue/catalogue)]
     [flash-message/component :top]
     (text :t.catalogue/intro)
     (if (or @(rf/subscribe [::full-catalogue :fetching?])
             @(rf/subscribe [::full-catalogue-tree :fetching?])
             @(rf/subscribe [::draft-applications :fetching?]))
       [spinner/big]
       [:div
        (when @(rf/subscribe [:logged-in])
          [:<>
           [draft-application-list]
           [cart/cart-list-container]
           [:h2 (text :t.catalogue/apply-resources)]])
        (when (:enable-catalogue-tree config)
          [catalogue-tree])
        (when (:enable-catalogue-table config)
          [catalogue-table])])]))
