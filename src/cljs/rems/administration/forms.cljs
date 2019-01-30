(ns rems.administration.forms
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-forms nil}))

(defn- fetch-forms []
  (fetch "/api/forms/" {:handler #(rf/dispatch [::fetch-forms-result %])}))

(rf/reg-fx ::fetch-forms (fn [_] (fetch-forms)))

(rf/reg-event-db
 ::fetch-forms-result
 (fn [db [_ forms]]
   (-> db
       (assoc ::forms forms)
       (dissoc ::loading?))))

(rf/reg-sub ::forms (fn [db _] (::forms db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ sorting]]
   (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :title
        :sort-order :asc})))

(defn- to-create-form []
  [:a.btn.btn-primary
   {:href "/#/administration/create-form"}
   (text :t.administration/create-form)])

(defn- to-view-form [form]
  [:a.btn.btn-primary
   {:href (str "/#/administration/forms/" (:id form))}
   (text :t.administration/view)])

(defn- forms-columns []
  {:organization {:header #(text :t.administration/organization)
                  :value :organization}
   :title {:header #(text :t.administration/title)
           :value :title}
   :start {:header #(text :t.administration/created)
           :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :active)}
   :commands {:value (fn [form] [to-view-form form])
              :sortable? false
              :filterable? false}})

(defn- forms-list
  "List of forms"
  [forms sorting]
  [table/component
   (forms-columns)
   [:organization :title :start :end :active :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   forms])

(defn forms-page []
  (let [forms (rf/subscribe [::forms])
        sorting (rf/subscribe [::sorting])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [administration-navigator-container]
             [:h2 (text :t.administration/forms)]]
            (if @loading?
              [[spinner/big]]
              [[to-create-form]
               [forms-list @forms @sorting]])))))
