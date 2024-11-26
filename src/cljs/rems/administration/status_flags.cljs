(ns rems.administration.status-flags
  (:require [better-cond.core :as b]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.text :refer [text text-format get-localized-title localized]]))

;; TODO this should be in some util namespace
(defn- get-localized-title-for-anything [item]
  (or (get-localized-title item)
      (:resid item)
      (:form/internal-name item)
      (:title item)
      (localized (:organization/name item))))

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
     [atoms/checkbox {:id :display-archived
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

(defn- find-item-id [item]
  (some #(when-let [id (% item)] [% id])
        #{:catalogue-item/id :category/id :form/id :license/id :organization/id :resource/id :workflow/id}))

(defn- render-error-item [item]
  (let [[item-type id] (find-item-id item)]
    (case item-type
      :catalogue-item/id [:li
                          (text :t.administration/catalogue-item) ": "
                          [atoms/link {:target :_blank
                                       :href (str "/administration/catalogue-items/" id)
                                       :label (get-localized-title-for-anything item)}]]
      :category/id [:li
                    (text :t.administration/category) ": "
                    [atoms/link {:target :_blank
                                 :href (str "/administration/categories/" id)
                                 :label (localized (:category/title item))}]]
      :form/id [:li
                (text :t.administration/form) ": "
                [atoms/link {:target :_blank
                             :href (str "/administration/forms/" id)
                             :label (get-localized-title-for-anything item)}]]
      :license/id [:li
                   (text :t.administration/license) ": "
                   [atoms/link {:target :_blank
                                :href (str "/administration/licenses/" id)
                                :label (get-localized-title-for-anything item)}]]
      :organization/id [:li
                        (text :t.administration/organization) ": "
                        [atoms/link {:target :_blank
                                     :href (str "/administration/organizations/" id)
                                     :label (get-localized-title-for-anything item)}]]
      :resource/id [:li
                    (text :t.administration/resource) ": "
                    [atoms/link {:target :_blank
                                 :href (str "/administration/resources/" id)
                                 :label (get-localized-title-for-anything item)}]]
      :workflow/id [:li
                    (text :t.administration/workflow) ": "
                    [atoms/link {:target :_blank
                                 :href (str "/administration/workflows/" id)
                                 :label (get-localized-title-for-anything item)}]])))

(defn- render-with-preview [items]
  (r/with-let [expanded? (r/atom false)]
    (when (seq items)
      (let [item-count (count items)
            shown-items (cond->> items
                          (not @expanded?) (take 5))]
        [:div
         (into [:ul] (map render-error-item) shown-items)
         (when (> item-count 5)
           [atoms/action-link
            {:on-click #(swap! expanded? not)
             :label (if @expanded?
                      (text :t.collapse/hide)
                      (text-format :t.label/parens (text :t.collapse/show) (- item-count 5)))}])]))))

(defn- format-update-error [{:keys [type catalogue-items forms licenses resources workflows categories]}]
  [:<>
   [:p (text type)]
   [render-with-preview catalogue-items]
   [render-with-preview forms]
   [render-with-preview licenses]
   [render-with-preview resources]
   [render-with-preview workflows]
   [render-with-preview categories]])

(defn format-update-failure [{:keys [errors]}]
  (into [:div]
        (map format-update-error errors)))
