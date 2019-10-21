(ns rems.administration.blacklist
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]))

(defn blacklist-page []
  [:div
   [administration-navigator-container]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]])
