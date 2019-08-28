(ns rems.flash-message
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]))

(rf/reg-sub ::message (fn [db _] (::message db)))

(rf/reg-event-fx
 ::show-flash-message
 (fn [{:keys [db]} [_ message]]
   (.scrollTo js/window 0 0)
   ;; TODO: focus the message
   ;; TODO: be smarter about clearing the message
   ;; TODO: flash the message with CSS
   {:db (assoc db ::message message)}))

(defn show-success! [contents]
  (rf/dispatch [::show-flash-message {:status :success
                                      :contents contents}]))

(defn show-error! [contents]
  (rf/dispatch [::show-flash-message {:status :danger
                                      :contents contents}]))
(defn component []
  [atoms/flash-message @(rf/subscribe [::message])])

;;; Helpers for typical messages

(defn show-default-success! [description]
  (show-success! (str description ": Success.")))

(defn show-default-error! [description & more]
  (show-error! (str/join " " (concat [(str description ": Error.")]
                                     more))))

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
