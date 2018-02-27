(ns rems.guide-page
  (:require ;; [compojure.core :refer [GET defroutes]]
   [re-frame.core :as rf]
   [rems.actions :as actions]
   [rems.application :as application]
   ;; [rems.auth.auth :as auth]
   [rems.cart :as cart]
   [rems.catalogue :as catalogue]
   [rems.collapsible :as collapsible]
   ;; [rems.context :as context]
   ;; [rems.form :as form]
   ;; [rems.guide :refer :all]
   ;; [rems.home :as home]
   [rems.language-switcher :as language-switcher]
   ;; [rems.layout :as layout]
   [rems.navbar :as nav]
   [rems.phase :as phase]
   ;; [rems.util :as util]
   )
  (:require-macros [rems.guide-macros :refer [example]]))

(defn color-box [id hex]
  [:div.col-md-3
   [:row
    [:div.col-md-6.rectangle {:class id }]
    [:div.col-md-6.color-title hex]]])

(defn color-boxes []
  (let [theme @(rf/subscribe [:theme])]
    [:div.row
     [color-box "color-1" (:color1 theme)]
     [color-box "color-2" (:color2 theme)]
     [color-box "color-3" (:color3 theme)]
     [color-box "color-4" (:color4 theme)]]))

(defn alerts []
  [:div
   [:div.alert.alert-success "Success level message"]
   [:div.alert.alert-info "Info level message"]
   [:div.alert.alert-warning "Warning level message"]
   [:div.alert.alert-danger "Danger level message"]
   ])

(defn guide-page []
  ;; (binding [context/*root-path* "path/"
  ;;           context/*roles* #{:applicant}]
  ;;   (with-language :en
  [:div.container
   [:div.example-page
    [:h1 "Component Guide"]

    [:h2 "Colors"]
    (example "Brand colors" [color-boxes])
    (example "Alerts" [alerts])

    ;; [:h2 "Layout components"]
    ;; (layout/guide)
    [:h2 "Navigation"]
    [nav/guide]

    [:h2 "Language switcher widget"]
    (language-switcher/guide)

    ;; [:h2 "Login"]
    ;; (auth/guide)

    [:h2 "Catalogue components"]
    [catalogue/guide]

    [:h2 "Cart components"]
    [cart/guide]

    ;; [:h2 "Applications list"]
    ;; (applications/guide)

    [:h2 "Actions list"]
    [actions/guide]

    [:h2 "Collapsible component"]
    [collapsible/guide]

    [:h2 "Applications"]
    [application/guide]

    [:h2 "Misc components"]
    [phase/guide]
    ;; (home/guide)
    ]])
