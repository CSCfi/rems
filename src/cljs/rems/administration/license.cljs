(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field localized-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [license-attachment-link external-link readonly-checkbox document-title]]
            [rems.common.util :refer [andstr]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [get-localized-title text text-format]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ license-id]]
   {:db (assoc db ::loading? true)
    ::fetch-license [license-id]}))

(defn- fetch-license [license-id]
  (fetch (str "/api/licenses/" license-id)
         {:handler #(rf/dispatch [::fetch-license-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch license")}))

(rf/reg-fx ::fetch-license (fn [[license-id]] (fetch-license license-id)))

(rf/reg-event-db
 ::fetch-license-result
 (fn [db [_ license]]
   (-> db
       (assoc ::license license)
       (dissoc ::loading?))))

(rf/reg-sub ::license (fn [db _] (::license db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- license-view [license language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "license"
     :title [:span (andstr (get-in license [:organization :organization/short-name language]) "/") (get-localized-title license language)]
     :always [:div#license
              [inline-info-field (text :t.administration/organization) (get-in license [:organization :organization/name language])]
              [localized-info-field license {:localizations-key :title
                                             :label (text :t.administration/title)}]
              [inline-info-field (text :t.administration/type) (:licensetype license)]
              (case (:licensetype license)
                "link"
                (into [:<>]
                      (for [langcode @(rf/subscribe [:languages])
                            :let [textcontent (get-in license [:localizations langcode :textcontent])]]
                        [inline-info-field
                         (str (text :t.create-license/external-link)
                              " (" (str/upper-case (name langcode)) ")")
                         [:a {:target :_blank :href textcontent}
                          textcontent " " [external-link]]]))
                "text"
                [localized-info-field license {:localizations-key :textcontent
                                               :label (text :t.create-license/license-text)}]
                "attachment"
                (into [:<>]
                      (for [langcode @(rf/subscribe [:languages])
                            :let [attachment-id (get-in license [:localizations langcode :attachment-id])
                                  title (get-in license [:localizations langcode :title])]]
                        [inline-info-field
                         (str (text :t.create-license/license-attachment)
                              " (" (str/upper-case (name langcode)) ")")
                         (when (some? attachment-id)
                           [license-attachment-link attachment-id title])
                         {:box? false}]))
                nil)
              [inline-info-field (text :t.administration/active)
               [readonly-checkbox {:value (status-flags/active? license)}]]]}]

   (let [id (:id license)]
     [:div.col.commands
      [administration/back-button "/administration/licenses"]
      [roles/show-when roles/+admin-write-roles+
       [status-flags/enabled-toggle license #(rf/dispatch [:rems.administration.licenses/set-license-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle license #(rf/dispatch [:rems.administration.licenses/set-license-archived %1 %2 [::enter-page id]])]]])])

;; XXX: Duplicates much of license-view. One notable difference is that
;;      here the license text is only shown in the current language.
(defn- license-view-compact [license language]
  (into [:div.form-item
         [:h3.license-title (text-format :t.administration/license-field (get-localized-title license language))]]
        (concat (for [[langcode localization] (:localizations license)]
                  [inline-info-field
                   (str (text :t.administration/title)
                        " (" (str/upper-case (name langcode)) ")")
                   (:title localization)])
                [[inline-info-field (text :t.administration/type) (:licensetype license)]]
                (when (= "link" (:licensetype license))
                  (for [[langcode localization] (:localizations license)]
                    [inline-info-field
                     (str (text :t.create-license/external-link)
                          " (" (str/upper-case (name langcode)) ")")
                     [:a {:target :_blank :href (:textcontent localization)}
                      (:textcontent localization) " " [external-link]]]))
                (when (= "text" (:licensetype license))
                  (let [localization (get-in license [:localizations language])]
                    (when (:textcontent localization)
                      [[inline-info-field (text :t.create-license/license-text)
                        (:textcontent localization)]])))
                (when (= "attachment" (:licensetype license))
                  (for [[langcode localization] (:localizations license)]
                    (when (:attachment-id localization)
                      [inline-info-field
                       (str (text :t.create-license/license-attachment)
                            " (" (str/upper-case (name langcode)) ")")
                       [license-attachment-link (:attachment-id localization) (:title localization)]
                       {:box? false}]))))))

(defn licenses-view [licenses language]
  [collapsible/component
   {:id "licenses"
    :title (text :t.administration/licenses)
    :top-less-button? (> (count licenses) 5)
    :open? (<= (count licenses) 5)
    :collapse (if (seq licenses)
                (into [:div]
                      (for [license licenses]
                        [license-view-compact license language]))
                [:p (text :t.administration/no-licenses)])}])

(defn license-page []
  (let [license (rf/subscribe [::license])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration/navigator]
       [document-title (text :t.administration/license)]
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         [license-view @license @language])])))
