(ns rems.flash-message
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :as rf]
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

(defn- location-to-id [location]
  (str "flash-message-"
       (cond
         (keyword? location) (name location)
         (vector? location) (str (name (first location))
                                 "-"
                                 (second location)))))

(deftest location-to-id-test
  (is (= "flash-message-top" (location-to-id :top)))
  (is (= "flash-message-attachment-10" (location-to-id [:attachment 10]))))

(rf/reg-event-fx
 ::show-flash-message
 (fn [{:keys [db]} [_ message]]
   (focus/focus-element-async (str "#" (location-to-id (:location message))))
   ;; TODO: flash the message with CSS
   {:db (assoc db ::message (assoc message :expires (+ 500 (current-time-millis))))}))

(defn show-success! [location contents]
  (rf/dispatch [::show-flash-message {:status :success
                                      :location location
                                      :contents contents
                                      :page @(rf/subscribe [:page])}]))

(defn show-error! [location contents]
  (rf/dispatch [::show-flash-message {:status :danger
                                      :location location
                                      :contents contents
                                      :page @(rf/subscribe [:page])}]))

(defn component [location]
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
        (when (= location (:location message))
          [atoms/flash-message {:id (location-to-id (:location message))
                                :status (:status message)
                                :contents (:contents message)}])))}))

;;; Helpers for typical messages

(defn show-default-success! [location description]
  (show-success! location [:div#status-success.flash-message-title
                           (str description ": " (text :t.form/success))]))

(defn show-default-error! [location description & more]
  (show-error! location (into [:<> [:div#status-failed.flash-message-title
                                    (str description ": " (text :t.form/failed))]]
                              more)))

(defn format-errors [errors]
  ;; TODO: copied as-is from status-modal; consider refactoring
  (into [:<>]
        (for [error errors]
          [:p
           (when (:key error)
             (text (:key error)))
           (when (:type error)
             (text (:type error)))
           (when-let [text (:status-text error)] text)
           (when-let [text (:status error)]
             (str " (" text ")"))])))

(defn format-response-error [response]
  (if (:errors response)
    (format-errors (:errors response))
    (:status-text response)))

(defn default-success-handler [location description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! location description)
        (when on-success
          (on-success response)))
      (show-default-error! location description (format-response-error response)))))

(defn status-update-handler [location description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! location description)
        (when on-success
          (on-success response)))
      (show-default-error! location description (status-flags/format-update-failure response)))))

(defn default-error-handler [location description]
  (fn [response]
    (show-default-error! location description (format-response-error response))))
