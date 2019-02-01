(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.atoms :refer [info-field readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ license-id]]
   {:db (assoc db ::loading? true)
    ::fetch-license [license-id]}))

(defn- fetch-license [license-id]
  (fetch (str "/api/licenses/" license-id)
         {:handler #(rf/dispatch [::fetch-license-result %])}))

(rf/reg-fx ::fetch-license (fn [[license-id]] (fetch-license license-id)))

(rf/reg-event-db
 ::fetch-license-result
 (fn [db [_ license]]
   (-> db
       (assoc ::license license)
       (dissoc ::loading?))))

(rf/reg-sub ::license (fn [db _] (::license db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/licenses")}
   (text :t.administration/back)])

(defn- to-create-license []
  [:a.btn.btn-primary
   {:href "/#/administration/create-license"}
   (text :t.administration/create-license)])

(defn license-view [license language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "license"
     :title [:span (get-in license [:localizations language :title] (:title license))]
     :always (into [:div]
                   (concat
                    [[inline-info-field (text :t.administration/title) (:title license)]]
                    (for [[langcode localization] (:localizations license)]
                      [inline-info-field (str (text :t.administration/title)
                                              " "
                                              (str/upper-case (name langcode))) (:title localization)])
                    [[inline-info-field (text :t.administration/type) (:licensetype license)]
                     [inline-info-field (text :t.administration/start) (localize-time (:start license))]
                     [inline-info-field (text :t.administration/end) (localize-time (:end license))]
                     [inline-info-field (text :t.administration/active) [readonly-checkbox (:active license)]]]))}]
   [:div.col.commands [back-button]]])

(defn license-page []
  (let [license (rf/subscribe [::license])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [:h2 (text :t.administration/license)]
       (if @loading?
         [spinner/big]
         [license-view @license @language])])))
