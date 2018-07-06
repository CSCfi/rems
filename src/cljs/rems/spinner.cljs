(ns rems.spinner
  (:require [re-frame.core :as rf]))

(defn big []
  (let [theme @(rf/subscribe [:theme])]
    [:div.text-center
     [:i {:style {:display :inline-block
                  :color (:color2 theme :transparent)
                  :margin "32px"
                  :font-size "40px"}
          :class "fas fa-spinner fa-spin"}]]))

(defn small []
  [:i {:class "fas fa-spinner fa-spin"}])
