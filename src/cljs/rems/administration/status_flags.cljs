(ns rems.administration.status-flags
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [checkbox]]
            [rems.text :refer [text get-localized-title localized]]))

;; TODO this should be in some util namespace
(defn- get-localized-title-for-anything
  ([item]
   (get-localized-title-for-anything item @(rf/subscribe [:language])))
  ([item language]
   (or (get-localized-title item language)
       (:resid item)
       (:form/title item)
       (:title item)
       (localized (:organization/name item)))))

(defn- disable-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :enabled false)
                          [:span [text :t.administration/disable]
                           " \"" [get-localized-title-for-anything item] "\""])}
   (text :t.administration/disable)])

(defn- enable-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :enabled true)
                          [:span [text :t.administration/enable]
                           " \""  [get-localized-title-for-anything item] "\""])}
   (text :t.administration/enable)])

                                        ; TODO consider naming enabled-toggle-button
(defn enabled-toggle [item on-change]
  (if (or (:enabled item) (:enabled item))
    [disable-button item on-change]
    [enable-button item on-change]))


(defn- archive-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :archived true)
                          [:span [text :t.administration/archive]
                           " \"" [get-localized-title-for-anything item] "\""])}
   (text :t.administration/archive)])

(defn- unarchive-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :archived false)
                          [:span [text :t.administration/unarchive]
                           " \"" [get-localized-title-for-anything item] "\""])}
   (text :t.administration/unarchive)])

;; TODO consider naming archived-toggle-button
(defn archived-toggle [item on-change]
  (if (or (:archived item) (:archived item))
    [unarchive-button item on-change]
    [archive-button item on-change]))

(rf/reg-event-fx
 ::set-display-archived?
 (fn [{:keys [db]} [_ display-archived?]]
   {:db (assoc db ::display-archived? display-archived?)}))

(defn display-archived? [db]
  ;; coerce default nil to false, since there is no global place to initialize db
  (boolean (::display-archived? db)))

(rf/reg-sub ::display-archived? (fn [db _] (display-archived? db)))

(defn display-archived-toggle [on-change]
  (let [display-archived? @(rf/subscribe [::display-archived?])
        on-change (fn []
                    (rf/dispatch [::set-display-archived? (not display-archived?)])
                    (when on-change (on-change)))]
    [:div.form-check.form-check-inline.pointer {:style {:float "right"}}
     [checkbox {:id :display-archived
                :class :form-check-input
                :value display-archived?
                :on-change on-change}]
     [:label.form-check-label {:for :display-archived :on-click on-change}
      (text :t.administration/display-archived)]]))

(defn disabled-and-archived-explanation []
  [:p.mt-1 (text :t.administration/disabled-and-archived-explanation)])

(defn active? [item]
  (and (:enabled item)
       (not (:expired item))
       (not (:archived item))))

(defn- format-update-error [{:keys [type catalogue-items forms licenses resources workflows]}]
  (let [language @(rf/subscribe [:language])]
    [:<>
     [:p (text type)]
     (into [:ul]
           (for [ci catalogue-items]
             [:li
              (text :t.administration/catalogue-item) ": "
              [:a {:target :_blank
                   :href (str "/administration/catalogue-items/" (:id ci))}
               (get-localized-title-for-anything ci language)]]))
     (into [:ul]
           (for [f forms]
             [:li
              (text :t.administration/form) ": "
              [:a {:target :_blank
                   :href (str "/administration/forms/" (:id f))}
               (get-localized-title-for-anything f language)]]))
     (into [:ul]
           (for [lic licenses]
             [:li
              (text :t.administration/license) ": "
              [:a {:target :_blank
                   :href (str "/administration/licenses/" (:id lic))}
               (get-localized-title-for-anything lic language)]]))
     (into [:ul]
           (for [r resources]
             [:li
              (text :t.administration/resource) ": "
              [:a {:target :_blank
                   :href (str "/administration/resources/" (:id r))}
               (get-localized-title-for-anything r language)]]))
     (into [:ul]
           (for [w workflows]
             [:li
              (text :t.administration/workflow) ": "
              [:a {:target :_blank
                   :href (str "/administration/workflows/" (:id w))}
               (get-localized-title-for-anything w language)]]))]))

(defn format-update-failure [{:keys [errors]}]
  (into [:div]
        (map format-update-error errors)))
