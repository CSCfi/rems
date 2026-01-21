(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.format :as rfmt]
            [rems.application-list :as application-list]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.atoms :as atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.common.util :refer [andstr]]
            [rems.config]
            [rems.globals]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.tree :as tree]
            [rems.text :refer [text text-format get-localized-title localized]]
            [rems.util :refer [get-dom-element]]))

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
  (let [link (catalogue-item-more-info-url item @rems.config/current-language @rems.globals/config)]
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

(defn- click-apply-button [row-key root-id]
  (let [selector (rfmt/format "#%s [data-row='%s'] .apply-for-catalogue-item"
                              root-id row-key)]
    (some-> (get-dom-element selector)
            .click)))

(defn- row-command [item cart-item-ids]
  (cond
    (not (:enable-cart @rems.globals/config))
    [apply-button item]

    (contains? cart-item-ids (:id item))
    [cart/remove-from-cart-button item]

    :else
    [cart/add-to-cart-button item]))

(defn- perform-row-command [item cart-item-ids root-id]
  (when-not (:category/id item)
    (cond
      (not (:enable-cart @rems.globals/config))
      (click-apply-button (:id item) (name root-id))

      (contains? cart-item-ids (:id item))
      (rf/dispatch [:rems.cart/remove-item (:id item)])

      :else
      (rf/dispatch [:rems.cart/add-item item]))))

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
                                           [row-command item cart-item-ids])]}})
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
  (let [cart @(rf/subscribe [:rems.cart/cart])
        cart-item-ids (set (map :id cart))
        get-row-details-id (fn [id] 
                             (str (name ::catalogue-tree) "-" id "-details"))
        catalogue {:id ::catalogue-tree
                   :row-key #(or (some->> (:category/id %) (str "category_"))
                                 (:id %))
                   :show-matching-parents? (:catalogue-tree-show-matching-parents @rems.globals/config)
                   :columns [{:key :name
                              :value #(or (localized (:category/title %)) (get-localized-title %))
                              :sort-value #(vector (:category/display-order % 2147483647) ; can't use Java Integer/MAX_VALUE here but anything that is not set is last
                                                   (or (localized (:category/title %)) "_") ; "_" means not set i.e. item is last
                                                   (get-localized-title %))
                              :title (text :t.catalogue/header)
                              :content #(if-let [id (:category/id %)]
                                          [:div.my-2
                                           [:div.tree-header {:class (str "fs-depth-" (:depth % 0))}
                                            (localized (:category/title %))]
                                           (when-let [description (localized (:category/description %))]
                                             [:div.mt-3 {:id (get-row-details-id id)}
                                              description])]
                                          [:div (get-localized-title %)])
                              :col-span #(if (:category/id %) 2 1)}
                             {:key :commands
                              :content #(when-not (:category/id %)
                                          [:div.commands.flex-nowrap.justify-content-end
                                           [catalogue-item-more-info %]
                                           (when @roles/logged-in?
                                             [row-command % cart-item-ids])])
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
                   :row-action (fn [row]
                                 (when @roles/logged-in?
                                   (perform-row-command (:value row) cart-item-ids ::catalogue-tree)))
                   :row-aria-label (fn [{:keys [value] :as row}]
                                     (cond
                                       (:category/id value) (str (text :t.administration/category)
                                                                 (andstr ": " (localized (:category/title value))))
                                       :else (str (text :t.administration/resource)
                                                  (andstr ": " (get-localized-title value)))))
                   :row-aria-describedby (fn [{:keys [value] :as row}]
                                           (when-let [id (:category/id value)]
                                             (get-row-details-id id)))
                   :default-sort-column :name
                   :aria-labelledby :catalogue-apply-resources}]
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

       [:h2#catalogue-apply-resources (text :t.catalogue/apply-resources)]])

    (when (:enable-catalogue-tree @rems.globals/config)
      (if @(rf/subscribe [::full-catalogue :fetching?])
        [spinner/big]
        [catalogue-tree]))

    (when (:enable-catalogue-table @rems.globals/config)
      (if @(rf/subscribe [::full-catalogue-tree :fetching?])
        [spinner/big]
        [catalogue-table]))]])
