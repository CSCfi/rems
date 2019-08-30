(ns rems.flash-message
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms]
            [rems.focus :as focus]
            [rems.text :refer [text]]))

(rf/reg-sub ::message (fn [db _] (::message db)))

(rf/reg-event-fx
 ::reset
 (fn [{:keys [db]} _]
   {:db (dissoc db ::message)}))

(defn- current-time-millis []
  (.getTime (js/Date.)))

(defn- expired? [message]
  (let [expires (get message :expires 0)]
    (< expires (current-time-millis))))

(rf/reg-event-fx
 ::show-flash-message
 (fn [{:keys [db]} [_ message]]
   ;; On Chrome, focusing the element scrolls it fully into view,
   ;; but on Firefox the element is hidden behind the navigation menu,
   ;; so explicit scrolling is needed.
   (.scrollTo js/window 0 0)
   (focus/focus-element-async "#flash-message")
   ;; TODO: flash the message with CSS
   {:db (assoc db ::message (assoc message :expires (+ 500 (current-time-millis))))}))

(defn show-success! [contents]
  (rf/dispatch [::show-flash-message {:status :success
                                      :contents contents
                                      :page @(rf/subscribe [:page])}]))

(defn show-error! [contents]
  (rf/dispatch [::show-flash-message {:status :danger
                                      :contents contents
                                      :page @(rf/subscribe [:page])}]))

(defn component []
  (reagent/create-class
   {:display-name "rems.flash-message/component"

    :component-will-unmount
    (fn [_this]
      (let [message @(rf/subscribe [::message])]
        (when (expired? message)
          (rf/dispatch [::reset]))))

    :reagent-render
    (fn []
      (let [message @(rf/subscribe [::message])]
        [atoms/flash-message message]))}))

;;; Helpers for typical messages

(defn show-default-success! [description]
  (show-success! [:div#status-success.flash-message-title
                  (str description ": " (text :t.form/success))]))

(defn show-default-error! [description & more]
  (show-error! (into [:<> [:div#status-failed.flash-message-title
                           (str description ": " (text :t.form/failed))]]
                     more)))

(defn default-success-handler [description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! description)
        (when on-success
          (on-success response)))
      (show-default-error! description))))

(defn status-update-handler [description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! description)
        (when on-success
          (on-success response)))
      (show-default-error! description (status-flags/format-update-failure response)))))

(defn default-error-handler [description]
  (fn [response]
    (show-default-error! description (:status-text response))))
