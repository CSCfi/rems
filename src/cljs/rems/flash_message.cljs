(ns rems.flash-message
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [rems.atoms :as atoms]
            [rems.text :refer [text]]))

(rf/reg-sub ::message (fn [db _] (::message db)))

(rf/reg-event-fx
 ::reset
 (fn [{:keys [db]} _]
   {:db (dissoc db ::message)}))

(rf/reg-event-fx
 ::show-flash-message
 (fn [{:keys [db]} [_ message]]
   (.scrollTo js/window 0 0)
   ;; TODO: focus the message
   ;; TODO: flash the message with CSS
   {:db (assoc db ::message message)}))

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
      (rf/dispatch [::reset]))

    :reagent-render
    (fn []
      (let [message @(rf/subscribe [::message])]
        [atoms/flash-message message]))}))

;;; Helpers for typical messages

(defn show-default-success! [description]
  (show-success! [:span#status-success
                  (str description ": " (text :t.form/success) ".")]))

(defn show-default-error! [description & more]
  (show-error! [:span#status-failed
                (str/join " " (concat [(str description ": " (text :t.form/failed) ".")]
                                      more))]))

(defn default-success-handler [description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! description)
        (when on-success
          (on-success response)))
      (show-default-error! description))))

(defn default-error-handler [description]
  (fn [response]
    (show-default-error! description (:status-text response))))
