(ns rems.guide-page
  (:require ;; [compojure.core :refer [GET defroutes]]
   [rems.actions :as actions]
   ;; [rems.applicant-info :as applicant-info]
   [rems.application :as application]
   ;; [rems.auth.auth :as auth]
   [rems.cart :as cart]
   [rems.catalogue :as catalogue]
   [rems.collapsible :as collapsible]
   ;; [rems.context :as context]
   ;; [rems.form :as form]
   ;; [rems.guide :refer :all]
   ;; [rems.home :as home]
   ;; [rems.info-field :as info-field]
   ;; [rems.layout :as layout]
   [rems.phase :as phase]
   ;; [rems.util :as util]
   ))

#_(defn color-box [id hex]
    [:div.col-md-3
     [:row
      [:div.col-md-6.rectangle {:class id }]
      [:div.col-md-6.color-title hex]]])

#_(defn color-boxes []
    [:div.row
     [color-box "color-1" (util/get-theme-attribute :color1)]
     [color-box "color-2" (util/get-theme-attribute :color2)]
     [color-box "color-3" (util/get-theme-attribute :color3)]
     [color-box "color-4" (util/get-theme-attribute :color4)]])

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

    ;; [:h2 "Colors"]
    ;; (example "Brand colors" (color-boxes))
    ;; (example "Alerts" (alerts))

    ;; [:h2 "Layout components"]
    ;; (layout/guide)

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

    ;; [:h2 "Applicant Information"]
    ;; (info-field/guide)
    ;; (applicant-info/guide)

    [:h2 "Applications"]
    (application/guide)

    [:h2 "Misc components"]
    [phase/guide]
    ;; (home/guide)
    ]])
