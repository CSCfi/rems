(ns rems.spinner
  (:require [re-frame.core :as rf]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn big
  "Big spinner for indicating loading or in-progress state."
  []
  (let [theme @(rf/subscribe [:theme])]
    [:div.text-center
     [:i {:style {:display :inline-block
                  :color (:color2 theme :transparent)
                  :margin "32px"
                  :font-size "40px"}
          :class "fas fa-spinner fa-spin"}
      [:span.sr-only (text :t.form/please-wait)]]]))

(defn small
  "Small spinner for indicating loading or in-progress state."
  []
  [:i {:class "fas fa-spinner fa-spin"}
   [:span.sr-only (text :t.form/please-wait)]])

(defn guide
  []
  [:div
   (component-info small)
   (example "small spinner"
            [small])
   (component-info big)
   (example "big spinner"
            [big])])
