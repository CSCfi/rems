(ns rems.administration.status-flags
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [checkbox]]
            [rems.text :refer [text get-localized-title localized]]))

;; TODO this should be in some util namespace
(defn- get-localized-title-for-anything [item]
  (or (get-localized-title item)
      (:resid item)
      (:form/internal-name item)
      (:title item)
      (localized (:organization/name item))))

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

(defn- plus-others [item-count]
  (when (> item-count 5)
    [:li (str "... plus " (- item-count 5) " more")]))

(defn- take-preview-sample [items]
  (take 5 items))

(defn- format-update-error [{:keys [type catalogue-items forms licenses resources workflows categories]}]
  [:<>
   [:p (text type)]
   (when (seq catalogue-items)
     [:ul
      (into [:<>] (for [ci (take-preview-sample catalogue-items)]
                    [:li
                     (text :t.administration/catalogue-item) ": "
                     [:a {:target :_blank
                          :href (str "/administration/catalogue-items/" (:id ci))}
                      (get-localized-title-for-anything ci)]]))
      [plus-others (count catalogue-items)]])
   (when (seq forms)
     [:ul
      (into [:<>] (for [f (take-preview-sample forms)]
                    [:li
                     (text :t.administration/form) ": "
                     [:a {:target :_blank
                          :href (str "/administration/forms/" (:id f))}
                      (get-localized-title-for-anything f)]]))
      [plus-others (count forms)]])
   (when (seq licenses)
     [:ul
      (into [:<>] (for [lic (take-preview-sample licenses)]
                    [:li
                     (text :t.administration/license) ": "
                     [:a {:target :_blank
                          :href (str "/administration/licenses/" (:id lic))}
                      (get-localized-title-for-anything lic)]]))
      [plus-others (count licenses)]])
   (when (seq resources)
     [:ul
      (into [:<>] (for [r (take-preview-sample resources)]
                    [:li
                     (text :t.administration/resource) ": "
                     [:a {:target :_blank
                          :href (str "/administration/resources/" (:id r))}
                      (get-localized-title-for-anything r)]]))
      [plus-others (count resources)]])
   (when (seq workflows)
     [:ul
      (into [:<>] (for [w (take-preview-sample workflows)]
                    [:li
                     (text :t.administration/workflow) ": "
                     [:a {:target :_blank
                          :href (str "/administration/workflows/" (:id w))}
                      (get-localized-title-for-anything w)]]))
      [plus-others (count workflows)]])
   (when (seq categories)
     [:ul
      (into [:<>] (for [cat (take-preview-sample categories)]
                    [:li
                     (text :t.administration/category) ": "
                     [:a {:target :_blank
                          :href (str "/administration/categories/" (:category/id cat))}
                      (localized (:category/title cat))]]))
      [plus-others (count categories)]])])

(defn format-update-failure [{:keys [errors]}]
  (into [:div]
        (map format-update-error errors)))
