(ns rems.administration.status-flags
  (:require [re-frame.core :as rf]
            [rems.text :refer [text]]))

(defn- disable-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(on-change (assoc item :enabled false))}
   (text :t.administration/disable)])

(defn- enable-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(on-change (assoc item :enabled true))}
   (text :t.administration/enable)])

(defn enabled-toggle [item on-change]
  (if (:enabled item)
    [disable-button item on-change]
    [enable-button item on-change]))


(defn- archive-button [item on-change]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(on-change (assoc item :archived true))}
   (text :t.administration/archive)])

(defn- unarchive-button [item on-change]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(on-change (assoc item :archived false))}
   (text :t.administration/unarchive)])

(defn archived-toggle [item on-change]
  (if (:archived item)
    [unarchive-button item on-change]
    [archive-button item on-change]))


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
