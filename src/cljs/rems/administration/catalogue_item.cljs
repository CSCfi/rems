(ns rems.administration.catalogue-item
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.atoms :refer [info-field readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-item-id]]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue-item [catalogue-item-id]}))

(defn- fetch-catalogue-item [catalogue-item-id]
  (fetch (str "/api/catalogue-items/" catalogue-item-id)
         {:handler #(rf/dispatch [::fetch-catalogue-item-result %])}))

(rf/reg-fx ::fetch-catalogue-item (fn [[catalogue-item-id]] (fetch-catalogue-item catalogue-item-id)))

(rf/reg-event-db
 ::fetch-catalogue-item-result
 (fn [db [_ catalogue-item]]
   (-> db
       (assoc ::catalogue-item catalogue-item)
       (dissoc ::loading?))))

(rf/reg-sub ::catalogue-item (fn [db _] (::catalogue-item db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [:button.btn.btn-secondary
   {:type :button
    :on-click #(dispatch! "/#/administration/catalogue-items")}
   (text :t.administration/back)])

(defn- to-create-catalogue-item []
  [:a.btn.btn-primary
   {:href "/#/administration/create-catalogue-item"}
   (text :t.administration/create-catalogue-item)])

(defn catalogue-item-view [catalogue-item language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "catalogue-item"
     :title [:span (get-in catalogue-item [:localizations language :title] (:title catalogue-item))]
     :always (into [:div]
                   (concat
                    [[inline-info-field (text :t.administration/title) (:title catalogue-item)]]
                    (for [[langcode localization] (:localizations catalogue-item)]
                      [inline-info-field (str (text :t.administration/title)
                                              " "
                                              (str/upper-case (name langcode))) (:title localization)])
                    [[inline-info-field (text :t.administration/resource)
                      [:a {:href (str "#/administration/resources/" (:resource-id catalogue-item))}
                       (:resource-name catalogue-item)]]
                     [inline-info-field (text :t.administration/workflow)
                      [:a {:href (str "#/administration/workflows/" (:wfid catalogue-item))}
                       (:workflow-name catalogue-item)]]
                     [inline-info-field (text :t.administration/form) [:a {:href (str "#/administration/workflows/" (:formid catalogue-item))}
                                                                       (:form-name catalogue-item)]]
                     [inline-info-field (text :t.administration/start) (localize-time (:start catalogue-item))]
                     [inline-info-field (text :t.administration/end) (localize-time (:end catalogue-item))]
                     [inline-info-field (text :t.administration/active) [readonly-checkbox (not (:expired catalogue-item))]]]))}]
   [:div.col.commands [back-button]]])

(defn catalogue-item-page []
  (let [catalogue-item (rf/subscribe [::catalogue-item])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [document-title (text :t.administration/catalogue-item)]
       (if @loading?
         [spinner/big]
         [catalogue-item-view @catalogue-item @language])])))
