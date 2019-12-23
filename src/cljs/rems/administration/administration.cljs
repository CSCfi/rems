(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.navbar :as navbar]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn navigator []
  [:div.navbar.mb-4.mr-auto.ml-auto
   [navbar/nav-link "/administration/catalogue-items" (text :t.administration/catalogue-items)]
   [navbar/nav-link "/administration/resources" (text :t.administration/resources)]
   [navbar/nav-link "/administration/forms" (text :t.administration/forms)]
   [navbar/nav-link "/administration/workflows" (text :t.administration/workflows)]
   [navbar/nav-link "/administration/licenses" (text :t.administration/licenses)]
   [navbar/nav-link "/administration/applications" (text :t.administration/applications)]
   [navbar/nav-link "/administration/blacklist" (text :t.administration/blacklist)]])

(defn guide []
  [:div
   (component-info navigator)
   (example "navigator"
            [navigator])])
