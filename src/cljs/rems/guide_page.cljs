(ns rems.guide-page
  (:require [re-frame.core :as rf]
            [rems.actions.components]
            [rems.administration.administration :as administration]
            [rems.application :as application]
            [rems.application-list :as application-list]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.guide-util :refer [example]]
            [rems.language-switcher :as language-switcher]
            [rems.navbar :as nav]
            [rems.phase :as phase]
            [rems.profile :as profile]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.tree :as tree]
            [rems.user :as user]))

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
   [:div.alert.alert-primary "Primary level message"]
   [:div.alert.alert-secondary "Secondary level message"]
   [:div.alert.alert-success "Success level message"]
   [:div.alert.alert-danger "Danger level message"]
   [:div.alert.alert-warning "Warning level message"]
   [:div.alert.alert-info "Info level message"]
   [:div.alert.alert-light "Light message"]
   [:div.alert.alert-dark "Dark message"]])

(defn text-and-background []
  [:div
   [:div.bg-primary "Primary background"]
   [:div.bg-secondary "Secondary background"]
   [:div.bg-success "Success background"]
   [:div.bg-danger "Danger background"]
   [:div.bg-warning "Warning background"]
   [:div.bg-info "Info background"]
   [:div.text-primary "Primary text"]
   [:div.text-secondary "Secondary text"]
   [:div.text-success "Success text"]
   [:div.text-danger "Danger text"]
   [:div.text-warning "Warning text"]
   [:div.text-info "Info text"]
   [:div.text-dark.bg-white "Dark text and white background"]
   [:div.text-dark.bg-light "Dark text and light background"]
   [:div.text-light.bg-dark "Light text and dark background"]
   [:div.text-muted.bg-dark "Muted text and dark background"]
   [:div.text-white.bg-dark "White text and dark background"]])

(defn buttons []
  [:div {:style {:display :flex :flex-direction :row :flex-wrap :wrap}}
   [:div.btn.btn-primary "Primary button"]
   [:div.btn.btn-secondary "Secondary button"]
   [:div.btn.btn-success "Success button"]
   [:div.btn.btn-info "Info button"]
   [:div.btn.btn-light "Light button"]
   [:div.btn.btn-dark "Dark button"]
   [:div.btn.btn-warning "Warning button"]
   [:div.btn.btn-danger "Danger button"]
   [:div.btn.btn-link "Link button"]])

(defn guide-page []
  [:div.container
   [:div.example-page
    [document-title "Component Guide"]
    [flash-message/component :top]

    [:h2 "Colors"]
    (example "Brand colors" [color-boxes])
    (example "Alerts" [alerts])
    (example "Text and background colors" [text-and-background])

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

    [:h2 "Cart components"]
    [cart/guide]

    [:h2 "Application list"]
    [application-list/guide]

    [:h2 "Collapsible component"]
    [collapsible/guide]

    [:h2 "Applications"]
    [application/guide]

    [:h2 "Form fields"]
    [fields/guide]

    [:h2 "Application actions"]
    [rems.actions.components/guide]

    [:h2 "Profile"]
    [profile/guide]

    [:h2 "Administration"]
    [administration/guide]

    [:h2 "Misc components"]
    [table/guide]
    [tree/guide]
    [dropdown/guide]
    [phase/guide]
    [atoms/guide]
    [user/guide]]])
