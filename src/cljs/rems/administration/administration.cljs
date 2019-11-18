(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.navbar :as navbar]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn administration-navigator [selected]
  [:div.navbar.mb-4.mr-auto.ml-auto
   [navbar/nav-link "/administration/catalogue-items" (text :t.administration/catalogue-items)]
   [navbar/nav-link "/administration/resources" (text :t.administration/resources)]
   [navbar/nav-link "/administration/forms" (text :t.administration/forms)]
   [navbar/nav-link "/administration/workflows" (text :t.administration/workflows)]
   [navbar/nav-link "/administration/licenses" (text :t.administration/licenses)]
   [navbar/nav-link "/administration/blacklist" (text :t.administration/blacklist)]])

(defn administration-navigator-container
  "Component for showing a navigator in the administration pages.

  Subscribes to current page to show the link as selected. The pure functional version is `administration-navigator`"
  []
  (let [page (rf/subscribe [:page])]
    [administration-navigator @page]))

(defn guide []
  [:div
   (component-info administration-navigator)
   (example "administration-navigator with resources selected"
            [administration-navigator :rems.administration/resources])])
