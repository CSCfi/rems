(ns rems.administration.catalogue-items
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.catalogue-util :refer [get-catalogue-item-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch [::fetch-catalogue]}))

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]}]
   (fetch "/api/catalogue-items/" {:url-params {:expand :names
                                                :disabled true
                                                :expired (::display-old? db)
                                                :archived (::display-old? db)}
                                   :handler #(rf/dispatch [::fetch-catalogue-result %])
                                   :error-handler status-modal/common-error-handler!})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-sub ::catalogue (fn [db _] (::catalogue db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ item description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/catalogue-items/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-catalogue]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :name
        :sort-order :asc})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-catalogue]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-catalogue-item []
  [:a.btn.btn-primary
   {:href "/#/administration/create-catalogue-item"}
   (text :t.administration/create-catalogue-item)])

(defn- to-catalogue-item [catalogue-item-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/catalogue-items/" catalogue-item-id)}
   (text :t.administration/view)])

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :resource {:header #(text :t.administration/resource)
              :value (fn [row]
                       [:a {:href (str "#/administration/resources/" (:resource-id row))}
                        (:resource-name row)])
              :sort-value :resource-name
              :filter-value :resource-name}
   :form {:header #(text :t.administration/form)
          :value (fn [row]
                   [:a {:href (str "#/administration/forms/" (:formid row))}
                    (:form-name row)])
          :sort-value :form-name
          :filter-value :form-name}
   :workflow {:header #(text :t.administration/workflow)
              :value (fn [row]
                       [:a {:href (str "#/administration/workflows/" (:wfid row))}
                        (:workflow-name row)])
              :sort-value :workflow-name
              :filter-value :workflow-name}
   :created {:header #(text :t.administration/created)
             :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   ;; TODO: active means not-expired currently. it should maybe mean (and not-expired enabled not-archived)
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox not :expired)}
   :commands {:values (fn [item]
                        [[to-catalogue-item (:id item)]
                         [status-flags/enabled-toggle item #(rf/dispatch [::update-catalogue-item %1 %2])]
                         [status-flags/archived-toggle item #(rf/dispatch [::update-catalogue-item %1 %2])]])
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language sorting filtering]
  [table/component
   {:column-definitions (catalogue-columns language)
    :visible-columns [:name :resource :form :workflow :created :end :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items items}])

(defn catalogue-items-page []
  (into [:div
         [administration-navigator-container]
         [:h2 (text :t.administration/catalogue-items)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-catalogue-item]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [catalogue-list
            @(rf/subscribe [::catalogue])
            @(rf/subscribe [:language])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
