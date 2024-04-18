(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.atoms :as atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.config]
            [rems.globals]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.tree :as tree]
            [rems.text :refer [text text-format get-localized-title]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::catalogue ::draft-applications)
    :dispatch-n [[:rems.table/reset]
                 (when @roles/logged-in?
                   [::draft-applications])
                 (when (:enable-catalogue-tree @rems.globals/config)
                   [::full-catalogue-tree])
                 (when (:enable-catalogue-table @rems.globals/config)
                   [::full-catalogue])]}))

(fetcher/reg-fetcher ::full-catalogue "/api/catalogue?join-organization=false")
(fetcher/reg-fetcher ::full-catalogue-tree "/api/catalogue/tree?join-organization=false" {:result :roots})

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   (rf/subscribe [::full-catalogue]))
 (fn [catalogue _]
   (->> catalogue
        (filter :enabled)
        (remove :expired))))

(defn- filter-drafts-only [applications]
  (filter form-fields-editable? applications))

(fetcher/reg-fetcher ::draft-applications "/api/my-applications" {:result filter-drafts-only})

;;;; UI

(defn- catalogue-item-more-info [item]
  (let [link (catalogue-item-more-info-url item @rems.config/language-or-default @rems.globals/config)]
    (when link
      [:a.btn.btn-link
       {:href link
        :target :_blank
        :aria-label (str (text :t.catalogue/more-info)
                         ": "
                         (get-localized-title item)
                         ", "
                         (text :t.link/opens-in-new-window))}
       (text :t.catalogue/more-info) " " [external-link]])))

(defn- apply-button [item]
  [atoms/link {:class "btn btn-primary apply-for-catalogue-item"
               :aria-label (text-format :t.label/default
                                        (text :t.cart/apply)
                                        (get-localized-title item))}
   (str "/application?items=" (:id item))
   (text :t.cart/apply)])

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:rems.cart/cart])])
 (fn [[catalogue cart] _]
   (let [cart-item-ids (set (mapv :id cart))]
     (mapv (fn [item]
             {:key (:id item)
              :name {:value (get-localized-title item)}
              :commands {:display-value [:div.commands.flex-nowrap.justify-content-end
                                         [catalogue-item-more-info item]
                                         (when @roles/logged-in?
                                           (if (:enable-cart @rems.globals/config)
                                             (if (contains? cart-item-ids (:id item))
                                               [cart/remove-from-cart-button item]
                                               [cart/add-to-cart-button item])
                                             (apply-button item)))]}})
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
  [table/standard {:id ::catalogue
                   :columns [{:key :name
                              :title (text :t.catalogue/header)}
                             {:key :commands
                              :sortable? false
                              :filterable? false
                              :aria-label (text :t.actions/commands)}]
                   :rows [::catalogue-table-rows]
                   :default-sort-column :name}])

(defn- catalogue-tree []
  (let [language @rems.config/language-or-default
        cart @(rf/subscribe [:rems.cart/cart])
        cart-item-ids (set (map :id cart))
        catalogue {:id ::catalogue-tree
                   :row-key #(or (some->> (:category/id %) (str "category_"))
                                 (:id %))
                   :show-matching-parents? (:catalogue-tree-show-matching-parents @rems.globals/config)
                   :columns [{:key :name
                              :value #(or (get (:category/title %) language) (get-localized-title %))
                              :sort-value #(vector (:category/display-order % 2147483647) ; can't use Java Integer/MAX_VALUE here but anything that is not set is last
                                                   (get (:category/title %) language "_") ; "_" means not set i.e. item is last
                                                   (get-localized-title %))
                              :title (text :t.catalogue/header)
                              :content #(if (:category/id %)
                                          [:div.my-2
                                           [:h3.mb-0 {:class (str "fs-depth-" (:depth % 0))}
                                            (get (:category/title %) language)]
                                           (when-let [description (get (:category/description %) language)]
                                             [:div.mt-3 description])]
                                          [:div (get-localized-title %)])
                              :col-span #(if (:category/id %) 2 1)}
                             {:key :commands
                              :content #(when-not (:category/id %)
                                          [:div.commands.flex-nowrap.justify-content-end
                                           [catalogue-item-more-info %]
                                           (when @roles/logged-in?
                                             (if (:enable-cart @rems.globals/config)
                                               (if (contains? cart-item-ids (:id %))
                                                 [cart/remove-from-cart-button %]
                                                 [cart/add-to-cart-button %])
                                               (apply-button %)))])
                              :aria-label (text :t.actions/commands)
                              :sortable? false
                              :filterable? false}]
                   :children #(concat (:category/items %) (:category/children %))
                   :rows [::full-catalogue-tree]
                   :row-filter (fn [row]
                                 (if (:category/id row)
                                   true ; always pass categories
                                   (and (:enabled row)
                                        (not (:expired row)))))
                   :default-sort-column :name}]
    [:div.mt-2rem
     [tree/search catalogue]
     [tree/tree catalogue]]))

(defn catalogue-page []
  [:div
   [document-title (text :t.catalogue/catalogue)]
   [flash-message/component :top]
   (text :t.catalogue/intro)
   [:div
    (when @roles/logged-in?
      [:<>
       [draft-application-list]

       (when (:enable-cart @rems.globals/config)
         (when-not (or @(rf/subscribe [::full-catalogue :fetching?])
                       @(rf/subscribe [::full-catalogue-tree :fetching?]))
           [cart/cart-list-container]))

       [:h2 (text :t.catalogue/apply-resources)]])

    (when (:enable-catalogue-tree @rems.globals/config)
      (if @(rf/subscribe [::full-catalogue :fetching?])
        [spinner/big]
        [catalogue-tree]))

    (when (:enable-catalogue-table @rems.globals/config)
      (if @(rf/subscribe [::full-catalogue-tree :fetching?])
        [spinner/big]
        [catalogue-table]))]])
