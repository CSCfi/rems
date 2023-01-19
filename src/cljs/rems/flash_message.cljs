(ns rems.flash-message
  (:require [clojure.test :refer [deftest is]]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms]
            [rems.focus :as focus]
            [rems.text :refer [text text-format]]))

(defn- location-to-id [location]
  (let [_ (assert (keyword? location))]
    (str "flash-message-" (name location))))

(deftest test-location-to-id
  (is (= "flash-message-top" (location-to-id :top))))

(defn- current-time-millis []
  (.getTime (js/Date.)))

(defn- expired? [message]
  (< (:expires message 0) (current-time-millis)))

(rf/reg-sub ::message
            (fn [db [_ location]]
              (get-in db [::message location])))
(rf/reg-event-fx ::reset
                 (fn [{:keys [db]} [_ location]]
                   {:db (if (some? location)
                          (update db ::message #(dissoc % location))
                          (dissoc db ::message))}))
(rf/reg-event-fx ::show-flash-message
                 (fn [{:keys [db]} [_ message {:keys [focus? timeout] :or {focus? true}}]]
                   (when focus?
                     (focus/focus-element-async (str "#" (location-to-id (:location message)))))
                   (when (some? timeout)
                     (js/setTimeout #(rf/dispatch [::reset (:location message)]) timeout))
                   ;; TODO: flash the message with CSS
                   {:db (assoc-in db
                                  [::message (:location message)]
                                  (assoc message :expires (+ 500 (current-time-millis))))}))

(defn clear-message! [location]
  (rf/dispatch [::reset location]))

(defn show-success! [location content & [opts]]
  (let [message {:status :success
                 :location location
                 :content content}]
    (rf/dispatch [::show-flash-message message opts])))

(defn show-default-success! [location description]
  (rf/dispatch [::reset])
  (show-success! location
                 [:div#status-success.flash-message-title description ": " [text :t.form/success]]))

(defn show-quiet-success!
  "Show a quiet success notification that doesn't by default steal focus."
  [location description & [opts]]
  (let [opts (merge {:focus? false}
                    opts)
        message {:status :success
                 :location location
                 :content (into [:<>
                                 [:div#status-success.flash-message-title description]]
                                (:content opts))}]
    (rf/dispatch [::show-flash-message message opts])))

(defn show-error! [location content & [opts]]
  (let [message {:status :danger
                 :location location
                 :content content}]
    (rf/dispatch [::show-flash-message message opts])))

(defn show-default-error! [location description & more]
  (rf/dispatch [::reset])
  (show-error! location (into [:<>
                               [:div#status-failed.flash-message-title description ": " [text :t.form/failed]]]
                              more)))

(defn show-warning! [location content & [opts]]
  (let [message {:status :warning
                 :location location
                 :content content}]
    (rf/dispatch [::show-flash-message message opts])))

(defn show-default-warning! [location description & {:keys [content focus?] :or {focus? true}}]
  (rf/dispatch [::reset])
  (show-warning! location
                 (into [:<>
                        [:div#status-warning.flash-message-title description ": " [text :t.form/success]]]
                       content)
                 {:focus? focus?}))

(defn component [location]
  (reagent/create-class
   {:display-name "rems.flash-message/component"

    :component-will-unmount
    (fn [_this]
      ;; XXX: this expiration check keeps component rendered after page transition,
      ;; for example navigation
      (when (expired? @(rf/subscribe [::message location]))
        (rf/dispatch [::reset location])))

    :reagent-render
    (fn []
      (when-some [message @(rf/subscribe [::message location])]
        [atoms/flash-message {:id (location-to-id location)
                              :status (:status message)
                              :content (:content message)}]))}))

(defn format-errors [errors]
  ;; TODO: copied as-is from status-modal; consider refactoring
  (into [:<>]
        (for [error errors]
          [:p
           (when (:key error)
             (text (:key error)))
           (when (:type error)
             (if (:args error)
               (apply text-format (:type error) (:args error))
               (text (:type error))))
           (when-let [text (:status-text error)] text)
           (when-let [text (:status error)]
             (str " (" text ")"))])))

(defn format-response-error [response]
  (if (:response response)
    (format-response-error (:response (clojure.walk/keywordize-keys response)))
    (if (:errors response)
      (format-errors (:errors response))
      (:status-text response))))

(defn default-success-handler [location description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! location description)
        (when on-success
          (on-success response)))
      (show-default-error! location description [format-response-error response]))
    response))

(defn status-update-handler [location description on-success]
  (fn [response]
    (if (:success response)
      (do
        (show-default-success! location description)
        (when on-success
          (on-success response)))
      (show-default-error! location description [status-flags/format-update-failure response]))
    response))

(defn default-error-handler [location description]
  (fn [response]
    (show-default-error! location description [format-response-error response])
    response))
