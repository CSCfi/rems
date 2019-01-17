(ns rems.catalogue
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-list :as application]
            [rems.application-util :refer [editable?]]
            [rems.atoms :refer [external-link]]
            [rems.cart :as cart]
            [rems.catalogue-util :refer [disabled-catalogue-item? get-catalogue-item-title urn-catalogue-item-link urn-catalogue-item?]]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [fetch unauthorized!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   (when (empty? (get-in db [:identity :roles]))
     (unauthorized!))
   (when (contains? (get-in db [:identity :roles]) :applicant)
     {:db (assoc db ::loading-catalogue? true)
      ::fetch-catalogue nil
      ::fetch-drafts nil})))

;;;; table sorting

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ sorting]]
   (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :name
        :sort-order :asc})))

;;;; catalogue

(defn- fetch-catalogue []
  (fetch "/api/catalogue/" {:handler #(rf/dispatch [::fetch-catalogue-result %])}))

(rf/reg-fx
 ::fetch-catalogue
 (fn [_]
   (fetch-catalogue)))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading-catalogue?))))

(rf/reg-sub
 ::loading-catalogue?
 (fn [db _]
   (::loading-catalogue? db)))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))


;;;; draft applications

(rf/reg-event-db
 ::fetch-drafts-result
 (fn [db [_ applications]]
   (assoc db ::draft-applications (filter (comp editable? :state)
                                          applications))))

(defn- fetch-drafts []
  (fetch "/api/applications/" {:handler #(rf/dispatch [::fetch-drafts-result %])}))

(rf/reg-fx
 ::fetch-drafts
 (fn [_]
   (fetch-drafts)))

(rf/reg-sub
 ::draft-applications
 (fn [db _]
   (::draft-applications db)))

;;;; UI

(defn- catalogue-item-title [item language]
  [:span (get-catalogue-item-title item language)])

(defn- catalogue-item-more-info [item config]
  (when (urn-catalogue-item? item)
    [:a.btn.btn-secondary {:href (urn-catalogue-item-link item config) :target :_blank}
     (text :t.catalogue/more-info) " " [external-link]]))

(defn- catalogue-columns [lang config]
  {:name {:header #(text :t.catalogue/header)
          :value (fn [item] [catalogue-item-title item lang])
          :sort-value #(get-catalogue-item-title % lang)}
   :commands {:values (fn [item] [[catalogue-item-more-info item config]
                                  [cart/add-to-cart-button item]])
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  [items language sorting config]
  [table/component
   (catalogue-columns language config) [:name :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   (filter (complement disabled-catalogue-item?) items)
   {:class "catalogue"}])

(defn- format-catalogue-items [app]
  (str/join ", " (map :title (:catalogue-items app))))

(defn draft-application-list [drafts]
  (when (seq drafts)
    [:div.drafts
     [:h4 (text :t.catalogue/continue-existing-application)]
     [table/component {:id {:value :id
                            :header #(text :t.actions/application)}
                       :resource {:value format-catalogue-items
                                  :header #(text :t.actions/resource)}
                       :modified {:value #(localize-time (or (:last-modified %) (:start %)))
                                  :header #(text :t.actions/last-modified)}
                       :view {:value application/view-button}}
      [:id :resource :modified :view] nil nil :id drafts]]))

(defn catalogue-page []
  (let [catalogue (rf/subscribe [::catalogue])
        drafts (rf/subscribe [::draft-applications])
        loading? (rf/subscribe [::loading-catalogue?])
        language (rf/subscribe [:language])
        sorting (rf/subscribe [::sorting])
        config (rf/subscribe [:rems.config/config])]
    (fn []
      [:div
       [:h2 (text :t.catalogue/catalogue)]
       (if @loading?
         [spinner/big]
         [:div
          [draft-application-list @drafts @language]
          [:h4 (text :t.catalogue/apply-resources)]
          [cart/cart-list-container @language]
          [catalogue-list @catalogue @language @sorting @config]])])))

(defn guide []
  [:div
   (component-info catalogue-item-title)
   (example "catalogue-item-title"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Item title"} nil]]]]])
   (example "catalogue-item-title in Finnish with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations"
                                       :localizations {:fi {:title "Suomenkielinen title"}
                                                       :en {:title "English title"}}} :en]]]]])
   (example "catalogue-item-title in English with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations"
                                       :localizations {:fi {:title "Suomenkielinen title"}
                                                       :en {:title "English title"}}} :fi]]]]])

   (component-info draft-application-list)
   (example "draft-list empty"
            [draft-application-list [] nil])
   (example "draft-list with two drafts"
            [draft-application-list [{:id 1 :catalogue-items [{:title "Item 5"}] :state "draft" :applicantuserid "alice"
                                      :start "1980-01-02T13:45:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}
                                     {:id 2 :catalogue-items [{:title "Item 3"}] :state "draft" :applicantuserid "bob"
                                      :start "1971-02-03T23:59:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}] nil])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            [catalogue-list [] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with two items"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with two items in reverse order"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil {:sort-column :name, :sort-order :desc}])
   (example "catalogue-list with three items, of which second is disabled"
            [catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with item linked to urn.fi"
            [catalogue-list [{:title "Item title" :resid "urn:nbn:fi:lb-201403262"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with item linked to example.org"
            [catalogue-list [{:title "Item title" :resid "urn:nbn:fi:lb-201403262"}] nil {:sort-column :name, :sort-order :asc} {:urn-organization "http://example.org/"}])])
