(ns rems.administration.licenses
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [readonly-checkbox]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch [::fetch-licenses]}))

(rf/reg-event-db
 ::fetch-licenses
 (fn [db]
   (fetch "/api/licenses/" {:url-params {:disabled true
                                         :expired (::display-old? db)
                                         :archived (::display-old? db)}
                            :handler #(rf/dispatch [::fetch-licenses-result %])})
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))

(rf/reg-event-fx
 ::update-license
 (fn [_ [_ item]]
   (put! "/api/licenses/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-licenses]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :title
        :sort-order :asc})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-licenses]}))

(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-licenses []
  [:a.btn.btn-primary
   {:href "/#/administration/create-license"}
   (text :t.administration/create-license)])

(defn- to-view-license [license-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/licenses/" license-id)}
   (text :t.administration/view)])

(defn- licenses-columns []
  {:title {:header #(text :t.administration/licenses)
           :value :title}
   :type {:header #(text :t.administration/type)
          :value :licensetype}
   :start {:header #(text :t.administration/created)
           :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox not :expired)}
   :commands {:values (fn [license]
                        [[to-view-license (:id license)]
                         [status-flags/enabled-toggle license #(rf/dispatch [::update-license %])]
                         [status-flags/archived-toggle license #(rf/dispatch [::update-license %])]])
              :sortable? false
              :filterable? false}})

(defn- licenses-list
  "List of licenses"
  [licenses sorting filtering]
  [table/component
   {:column-definitions (licenses-columns)
    :visible-columns [:title :type :start :end :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items licenses}])

(defn licenses-page []
  (into [:div
         [administration-navigator-container]
         [:h2 (text :t.administration/licenses)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-licenses]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [licenses-list
            @(rf/subscribe [::licenses])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
