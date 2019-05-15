(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.catalogue-util :refer [get-catalogue-item-title urn-catalogue-item-link urn-catalogue-item?]]
            [rems.guide-functions]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [fetch unauthorized!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   (if (roles/is-logged-in? (get-in db [:identity :roles]))
     {:db (dissoc db ::catalogue ::draft-applications)
      :dispatch-n [[::fetch-catalogue]
                   [::fetch-drafts]]}
     (do
       (unauthorized!)
       {}))))

;;;; table sorting

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db {:sort-order :asc
                                                :sort-column :name})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

;;;; catalogue

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]} _]
   (fetch "/api/catalogue"
          {:handler #(rf/dispatch [::fetch-catalogue-result %])})
   {:db (assoc db ::loading-catalogue? true)}))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading-catalogue?))))

(rf/reg-sub ::full-catalogue (fn [db _] (::catalogue db)))

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   (rf/subscribe [::full-catalogue]))
 (fn [catalogue _]
   (->> catalogue
        (filter :enabled)
        (remove :expired))))

(rf/reg-sub ::loading-catalogue? (fn [db _] (::loading-catalogue? db)))

;;;; draft applications

(rf/reg-event-fx
 ::fetch-drafts
 (fn [{:keys [db]} _]
   (fetch "/api/my-applications"
          {:handler #(rf/dispatch [::fetch-drafts-result %])})
   {:db (assoc db ::loading-drafts? true)}))

(rf/reg-event-db
 ::fetch-drafts-result
 (fn [db [_ applications]]
   (-> db
       (assoc ::draft-applications (filter form-fields-editable? applications))
       (dissoc ::loading-drafts?))))

(rf/reg-sub ::draft-applications (fn [db _] (::draft-applications db)))
(rf/reg-sub ::loading-drafts? (fn [db _] (::loading-drafts? db)))

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
  "Renders the catalogue using table.

  See `table/component`."
  [{:keys [items language sorting filtering config] :as params}]
  [table/component
   (merge {:column-definitions (catalogue-columns language config)
           :visible-columns [:name :commands]
           :id-function :id
           :items items
           :class "catalogue"}
          (when sorting {:sorting sorting})
          (when filtering {:filtering filtering}))])

(defn draft-application-list [drafts]
  (when (seq drafts)
    [:div.drafts
     [:h2 (text :t.catalogue/continue-existing-application)]
     [application-list/component
      {:visible-columns [:resource :last-activity :view]
       :items drafts}]]))

(defn catalogue-page []
  (let [language @(rf/subscribe [:language])
        catalogue @(rf/subscribe [::catalogue])
        loading-catalogue? @(rf/subscribe [::loading-catalogue?])
        drafts @(rf/subscribe [::draft-applications])
        loading-drafts? @(rf/subscribe [::loading-drafts?])]
    [:div
     [:h1 [document-title (text :t.catalogue/catalogue)]]
     (if (or loading-catalogue? loading-drafts?)
       [spinner/big]
       [:div
        [draft-application-list drafts]
        [:h2 (text :t.catalogue/apply-resources)]
        [cart/cart-list-container language]
        [catalogue-list
         {:items catalogue
          :language language
          :sorting (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
          :filtering (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))
          :config @(rf/subscribe [:rems.config/config])}]])]))

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
            [draft-application-list []])
   (example "draft-list with two drafts"
            [draft-application-list [{:application/id 1
                                      :application/resources [{:catalogue-item/title {:en "Item 5"}}]
                                      :application/state :application.state/draft
                                      :application/applicant "alice"
                                      :application/created "1980-01-02T13:45:00.000Z"
                                      :application/last-activity "2017-01-01T01:01:01:001Z"}
                                     {:application/id 2
                                      :application/resources [{:catalogue-item/title {:en "Item 3"}}]
                                      :application/state :application.state/draft
                                      :application/applicant "bob"
                                      :application/created "1971-02-03T23:59:00.000Z"
                                      :application/last-activity "2017-01-01T01:01:01:001Z"}]])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            [catalogue-list {:items [] :sorting {:sort-column :name, :sort-order :asc}}])
   (example "catalogue-list with two items"
            [catalogue-list {:items [{:title "Item title" :enabled true} {:title "Another title" :enabled true}] :sorting {:sort-column :name, :sort-order :asc}}])
   (example "catalogue-list with two items in reverse order"
            [catalogue-list {:items [{:title "Item title" :enabled true} {:title "Another title" :enabled true}] :sorting {:sort-column :name, :sort-order :desc}}])
   (example "catalogue-list with three items, of which second is disabled"
            [catalogue-list {:items [{:title "Item 1" :enabled true} {:title "Item 2 is disabled and should not be shown" :enabled false} {:title "Item 3"}] :sorting {:sort-column :name, :sort-order :asc}}])
   (example "catalogue-list with item linked to urn.fi"
            [catalogue-list {:items [{:title "Item title" :enabled true :resid "urn:nbn:fi:lb-201403262"}] :sorting {:sort-column :name, :sort-order :asc}}])
   (example "catalogue-list with item linked to example.org"
            [catalogue-list {:items [{:title "Item title" :enabled true :resid "urn:nbn:fi:lb-201403262"}] :sorting {:sort-column :name, :sort-order :asc} :config {:urn-organization "http://example.org/"}}])])
