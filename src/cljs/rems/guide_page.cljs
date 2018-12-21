(ns rems.guide-page
  (:require [re-frame.core :as rf]
            [rems.actions :as actions]
            [rems.application :as application]
            [rems.application-list :as application-list]
            [rems.atoms :as atoms]
            [rems.autocomplete :as autocomplete]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.collapsible :as collapsible]
            [rems.language-switcher :as language-switcher]
            [rems.modal :as modal]
            [rems.navbar :as nav]
            [rems.phase :as phase]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal])
  (:require-macros [rems.guide-macros :refer [example]]))

(defn color-box [id hex]
  [:div.col-md-3
   [:div.row
    [:div.col-md-6.rectangle {:class id}]
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
   [:div.alert.alert-danger "Danger level message"]])

(defn buttons []
  [:div
   [:div.row
    [:div.btn.btn-primary "Primary button"]
    [:div.btn.btn-secondary "Secondary button"]
    [:div.btn.btn-success "Success button"]
    [:div.btn.btn-info "Info button"]
    [:div.btn.btn-light "Light button"]
    [:div.btn.btn-dark "Dark button"]
    [:div.btn.btn-warning "Warning button"]
    [:div.btn.btn-danger "Danger button"]]])

(defn guide-page []
  [:div.container
   [:div.example-page
    [:h1 "Component Guide"]

    [:h2 "Colors"]
    (example "Brand colors" [color-boxes])
    (example "Alerts" [alerts])

    [:h2 "Buttons"]
    (example "Button" [buttons])

    [:h2 "Navigation"]
    [nav/guide]

    [:h2 "Spinner"]
    [spinner/guide]

    [:h2 "Language switcher widget"]
    [language-switcher/guide]

    [:h2 "Login"]
    [auth/guide]

    [:h2 "Catalogue components"]
    [catalogue/guide]

    [:h2 "Cart components"]
    [cart/guide]

    [:h2 "Application list"]
    [application-list/guide]

    [:h2 "Collapsible component"]
    [collapsible/guide]

    [:h2 "Modal"]
    [modal/guide]
    [status-modal/guide]

    [:h2 "Applications"]
    [application/guide]

    [:h2 "Misc components"]
    [autocomplete/guide]
    [phase/guide]
    [atoms/guide]]])
