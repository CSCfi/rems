(ns rems.administration.status-flags
  (:require [re-frame.core :as rf]
            [rems.text :refer [text get-localized-title]]))

(defn- disable-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :enabled false) (text :t.administration/disable))}
   (text :t.administration/disable)])

(defn- enable-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :enabled true) (text :t.administration/enable))}
   (text :t.administration/enable)])

(defn enabled-toggle [item on-change]
  (if (:enabled item)
    [disable-button item on-change]
    [enable-button item on-change]))


(defn- archive-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :archived true) (text :t.administration/archive))}
   (text :t.administration/archive)])

(defn- unarchive-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type :button
    :on-click #(on-change (assoc item :archived false) (text :t.administration/unarchive))}
   (text :t.administration/unarchive)])

(defn archived-toggle [item on-change]
  (if (:archived item)
    [unarchive-button item on-change]
    [archive-button item on-change]))

(defn display-archived-toggle [display-archived? on-change]
  [:div.form-check.form-check-inline {:style {:float "right"}}
   [:input.form-check-input {:type "checkbox"
                             :id "display-archived"
                             :checked display-archived?
                             :on-change #(on-change (not display-archived?))}]
   [:label.form-check-label {:for "display-archived"}
    (text :t.administration/display-archived)]])

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
               (get-localized-title ci language)]]))
     (into [:ul]
           (for [f forms]
             [:li
              (text :t.administration/form) ": "
              [:a {:target :_blank
                   :href (str "/administration/forms/" (:id f))}
               (:form/title f)]]))
     (into [:ul]
           (for [lic licenses]
             [:li
              (text :t.administration/license) ": "
              [:a {:target :_blank
                   :href (str "/administration/licenses/" (:id lic))}
               (get-localized-title lic language)]]))
     (into [:ul]
           (for [r resources]
             [:li
              (text :t.administration/resource) ": "
              [:a {:target :_blank
                   :href (str "/administration/resources/" (:id r))} (:resid r)]]))
     (into [:ul]
           (for [w workflows]
             [:li
              (text :t.administration/workflow) ": "
              [:a {:target :_blank
                   :href (str "/administration/workflows/" (:id w))} (:title w)]]))]))

(defn format-update-failure [{:keys [errors]}]
  (into [:div]
        (map format-update-error errors)))
