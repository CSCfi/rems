(ns rems.administration.resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.atoms :as atoms :refer [attachment-link external-link readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ resource-id]]
   {:db (assoc db ::loading? true)
    ::fetch-resource [resource-id]}))

(defn- fetch-resource [resource-id]
  (fetch (str "/api/resources/" resource-id)
         {:handler #(rf/dispatch [::fetch-resource-result %])}))

(rf/reg-fx ::fetch-resource (fn [[resource-id]] (fetch-resource resource-id)))

(rf/reg-event-db
 ::fetch-resource-result
 (fn [db [_ resource]]
   (-> db
       (assoc ::resource resource)
       (dissoc ::loading?))))

(rf/reg-sub ::resource (fn [db _] (::resource db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/resources"
   (text :t.administration/back)])

(defn license-view [license language]
  (into [:div.form-item
         [:h3 (text-format :t.administration/license-field (get-in license [:localizations language :title]))]
         [inline-info-field (text :t.administration/title) (:title license)]]
        (concat (for [[langcode localization] (:localizations license)]
                  [inline-info-field
                   (str (text :t.administration/title)
                        " (" (str/upper-case (name langcode)) ")")
                   (:title localization)])
                [[inline-info-field (text :t.administration/type) (:licensetype license)]
                 (case (:licensetype license)
                   "link" [inline-info-field
                           (text :t.create-license/external-link)
                           [:a {:target :_blank :href (:textcontent license)}
                            (:textcontent license) " " [external-link]]]
                   "text" [inline-info-field
                           (text :t.create-license/license-text)
                           (:textcontent license)]
                   nil)]
                (when (= "link" (:licensetype license))
                  (for [[langcode localization] (:localizations license)]
                    [inline-info-field
                     (str (text :t.create-license/external-link)
                          " (" (str/upper-case (name langcode)) ")")
                     [:a {:target :_blank :href (:textcontent localization)}
                      (:textcontent localization) " " [external-link]]]))
                (when (= "attachment" (:licensetype license))
                  (for [[langcode localization] (:localizations license)]
                    (when (:attachment-id localization)
                      [inline-info-field
                       (str (text :t.create-license/license-attachment)
                            " (" (str/upper-case (name langcode)) ")")
                       [attachment-link (:attachment-id localization) (:title localization)]
                       {:box? false}])))
                [[inline-info-field (text :t.administration/start) (localize-time (:start license))]
                 [inline-info-field (text :t.administration/end) (localize-time (:end license))]])))

(defn licenses-view [licenses language]
  [collapsible/component
   {:id "licenses"
    :title (text :t.administration/licenses)
    :top-less-button? (> (count licenses) 5)
    :open? (<= (count licenses) 5)
    :collapse (if (seq licenses)
                (into [:div]
                      (for [license licenses]
                        [license-view license language]))
                [:p (text :t.administration/no-licenses)])}])

(defn resource-view [resource language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "resource"
     :title [:span (andstr (:domain resource) "/") (:resid resource)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization resource)]
              [inline-info-field (text :t.administration/resource) (:resid resource)]
              [inline-info-field (text :t.administration/start) (localize-time (:start resource))]
              [inline-info-field (text :t.administration/end) (localize-time (:end resource))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox (not (:expired resource))]]]}]
   [licenses-view (:licenses resource) language]
   [:div.col.commands [back-button]]])

(defn resource-page []
  (let [resource (rf/subscribe [::resource])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [document-title (text :t.administration/resource)]
       (if @loading?
         [spinner/big]
         [resource-view @resource @language])])))
