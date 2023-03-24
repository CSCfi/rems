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
       (:form/internal-name item)
       (:title item)
       (localized (:organization/name item)))))

;; XXX: consider rename to enabled-toggle-button
(defn enabled-toggle
  ([item on-change] (enabled-toggle {} item on-change))
  ([opts item on-change]
   (let [enabled? (:enabled item false)
         label (if enabled?
                 :t.administration/disable
                 :t.administration/enable)]
     [:button.btn.btn-primary.button-min-width
      {:id (:id opts)
       :type :button
       :on-click #(on-change (update item :enabled not)
                             [:span [text label]
                              " \""  [get-localized-title-for-anything item] "\""])}
      (text label)])))

(defn enabled-toggle-action [{:keys [id on-change]} item]
  (let [enabled? (:enabled item false)
        label (if enabled?
                :t.administration/disable
                :t.administration/enable)]
    {:id id
     :class "toggle-enabled"
     :on-click #(on-change (update item :enabled not)
                           [:span [text label]
                            " \""  [get-localized-title-for-anything item] "\""])
     :label [text label]}))

;; XXX: consider rename to archived-toggle-button
(defn archived-toggle
  ([item on-change] (archived-toggle {} item on-change))
  ([opts item on-change]
   (let [archived? (:archived item false)
         label (if archived?
                 :t.administration/unarchive
                 :t.administration/archive)]
     [:button.btn.btn-primary.button-min-width
      {:id (:id opts)
       :type :button
       :on-click #(on-change (update item :archived not)
                             [:span [text label]
                              " \"" [get-localized-title-for-anything item] "\""])}
      (text label)])))

(defn archived-toggle-action [{:keys [id on-change]} item]
  (let [archived? (:archived item false)
        label (if archived?
                :t.administration/unarchive
                :t.administration/archive)]
    {:id id
     :class "toggle-archived"
     :on-click #(on-change (update item :archived not)
                           [:span [text label]
                            " \"" [get-localized-title-for-anything item] "\""])
     :label [text label]}))

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
    [:div.form-check.form-check-inline.pointer
     [checkbox {:id :display-archived
                :class :form-check-input
                :value display-archived?
                :on-change on-change}]
     [:label.form-check-label {:for :display-archived :on-click on-change}
      (text :t.administration/display-archived)]]))

(defn disabled-and-archived-explanation []
  [:p (text :t.administration/disabled-and-archived-explanation)])

(defn status-flags-intro [on-change]
  [:div.mt-1.d-flex.flex-row.align-items-start {:style {:gap "2rem"}}
   [disabled-and-archived-explanation]
   [display-archived-toggle on-change]])

(defn active? [item]
  (and (:enabled item)
       (not (:expired item))
       (not (:archived item))))

(defn- format-update-error [{:keys [type catalogue-items forms licenses resources workflows categories]}]
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
               (get-localized-title-for-anything w language)]]))
     (into [:ul]
           (for [cat categories]
             [:li
              (text :t.administration/category) ": "
              [:a {:target :_blank
                   :href (str "/administration/categories/" (:category/id cat))}
               (localized (:category/title cat))]]))]))

(defn format-update-failure [{:keys [errors]}]
  (into [:div]
        (map format-update-error errors)))
