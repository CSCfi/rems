(ns rems.administration.status-flags
  (:require [re-frame.core :as rf]
            [rems.text :refer [text]]))

(defn- disable-button [update-item-cmd item]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [update-item-cmd (assoc item :enabled false)])}
   (text :t.administration/disable)])

(defn- enable-button [update-item-cmd item]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [update-item-cmd (assoc item :enabled true)])}
   (text :t.administration/enable)])

(defn enabled-toggle [update-item-cmd item]
  (if (:enabled item)
    [disable-button update-item-cmd item]
    [enable-button update-item-cmd item]))


(defn- archive-button [update-item-cmd item]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [update-item-cmd (assoc item :archived true)])}
   (text :t.administration/archive)])

(defn- unarchive-button [update-item-cmd item]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [update-item-cmd (assoc item :archived false)])}
   (text :t.administration/unarchive)])

(defn archived-toggle [update-item-cmd item]
  (if (:archived item)
    [unarchive-button update-item-cmd item]
    [archive-button update-item-cmd item]))


(defn display-archived-toggle [display-archived-sub set-display-archived-cmd]
  (let [display-archived? @(rf/subscribe [display-archived-sub])
        toggle #(rf/dispatch [set-display-archived-cmd (not display-archived?)])]
    [:div.form-check.form-check-inline {:style {:float "right"}}
     [:input.form-check-input {:type "checkbox"
                               :id "display-archived"
                               :checked display-archived?
                               :on-change toggle}]
     [:label.form-check-label {:for "display-archived"}
      (text :t.administration/display-archived)]]))
