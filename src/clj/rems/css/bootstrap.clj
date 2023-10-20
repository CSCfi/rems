(ns rems.css.bootstrap
  (:require [garden.selectors :as s]
            [garden.units :as u]
            [rems.css.style-utils :refer [css-var theme-get theme-getx]]))

;; https://getbootstrap.com/docs/5.2/layout/breakpoints/
(def media-xxl "extra extra large: >= 1400px" {:min-width (u/px 1400)})
(def media-xl "extra large: >= 1200px" {:min-width (u/px 1200)})
(def media-lg "large: >= 992px" {:min-width (u/px 992)})
(def media-md "medium: >= 768px" {:min-width (u/px 768)})
(def media-sm "small: >= 576px" {:min-width (u/px 576)})
(def media-xs "extra small: < 576px" {:max-width (u/px 575)})

(defn bs-card []
  [(s/root) {:--bs-card-cap-padding-y (u/rem 0.75)
             :--bs-card-cap-padding-x (u/rem 1.25)
             #_--bs-card-inner-border-radius
             #_--bs-card-inner-border-radius}])

#_[:.nav-link
   :.btn-link
   {:color (theme-getx :nav-color :link-color)
    :font-weight (button-navbar-font-weight)
    :border 0} ; for button links
   [:&.active
    {:color (theme-getx :nav-active-color :color4)}]
   [:&:hover
    {:color (theme-getx :nav-hover-color :color4)}]]

;; TODO: override in bootstrap/reset-styles
;; override Bootstrap blue active color with its hover color
#_[".dropdown-item.active"
   ".dropdown-item:active"
   {:background-color "#e9ecef"}]
;; TODO: override in bootstrap/reset-styles
;; Bootstrap has inaccessible focus indicators in particular
;; for .btn-link and .btn-secondary, so we define our own.
#_[:a:focus :button:focus :.btn.focus :.btn:focus
   "h1[tabindex]:focus-within"
   {:outline 0
    :box-shadow "0 0 0 0.2rem rgba(38,143,255,.5) !important"}]

(defn bs-nav-link []
  [[(s/root) {:--bs-nav-link-color (css-var :--rems-nav-color (css-var :--rems-link-color))
              :--bs-nav-link-hover-color (css-var :--rems-link-hover-color (css-var :--rems-color4))
              #_:--bs-nav-link-font-size
              :--bs-nav-link-padding-y (u/rem 0.5)
              :--bs-nav-link-padding-x (u/rem 1)}]
   [:.nav-link {:--bs-navbar-active-color (css-var :--rems-link-hover-color (css-var :--rems-color4))
                :--bs-nav-link-font-weight (css-var :--rems-button-navbar-font-weight)}]])

(defn bs-btn-link []
  ;; XXX: bootstrap btn-link uses "--bs-btn-*" variables instead of "--bs-btn-link-*"
  [:.btn-link {:--bs-btn-font-weight (css-var :--rems-button-navbar-font-weight)
               :--bs-btn-color (css-var :--rems-nav-color (css-var :--rems-link-color))
               :--bs-btn-active-color (css-var :--rems-link-hover-color (css-var :--rems-color4))
               :--bs-btn-hover-color (css-var :--rems-link-hover-color (css-var :--rems-color4))
               :text-decoration "none"}])

;; (defn bs-btn []
;;   [(s/root) {:--bs-btn-font-weight }])

;; TODO: override in bootstrap/reset-styles
   ;; override Bootstrap blue active color with its hover color
#_[".dropdown-item.active"
   ".dropdown-item:active"
   {:background-color "#e9ecef"}]
   ;; TODO: override in bootstrap/reset-styles
   ;; Bootstrap has inaccessible focus indicators in particular
   ;; for .btn-link and .btn-secondary, so we define our own.
#_[:a:focus :button:focus :.btn.focus :.btn:focus
   "h1[tabindex]:focus-within"
   {:outline 0
    :box-shadow "0 0 0 0.2rem rgba(38,143,255,.5) !important"}]

(defn reset-styles []
  (list (bs-card)
        (bs-nav-link)
        (bs-btn-link)))
