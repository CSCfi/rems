(ns rems.css.styles
  (:require [garden.core :as g]
            [garden.def :refer [defstyles]]
            [garden.selectors :as s]
            [garden.stylesheet :as stylesheet]
            [garden.units :as u]
            [rems.context :as context]))

(defn- generate-at-font-faces []
  (list
    (stylesheet/at-font-face {:font-family "'Lato'"
                              :src "url('/font/Lato-Light.eot')"}
                             {:src "url('/font/Lato-Light.eot') format('embedded-opentype'), url('/font/Lato-Light.woff2') format('woff2'), url('/font/Lato-Light.woff') format('woff'), url('/font/Lato-Light.ttf') format('truetype')"
                              :font-weight 300
                              :font-style "normal"})
    (stylesheet/at-font-face {:font-family "'Lato'"
                              :src "url('/font/Lato-Regular.eot')"}
                             {:src "url('/font/Lato-Regular.eot') format('embedded-opentype'), url('/font/Lato-Regular.woff2') format('woff2'), url('/font/Lato-Regular.woff') format('woff'), url('/font/Lato-Regular.ttf') format('truetype')"
                              :font-weight 400
                              :font-style "normal"})
    (stylesheet/at-font-face {:font-family "'Lato'"
                              :src "url('/font/Lato-Bold.eot')"}
                             {:src "url('/font/Lato-Bold.eot') format('embedded-opentype'), url('/font/Lato-Bold.woff2') format('woff2'), url('/font/Lato-Bold.woff') format('woff'), url('/font/Lato-Bold.ttf') format('truetype')"
                              :font-weight 700
                              :font-style "normal"})))

(defn- generate-form-placeholder-styles []
  (list
    [".form-control::placeholder" {:color "#ccc"}] ; Standard
    [".form-control::-webkit-input-placeholder" {:color "#ccc"}] ; WebKit, Blink, Edge
    [".form-control:-moz-placeholder" {:color "#ccc"
                                       :opacity 1}] ; Mozilla Firefox 4 to 18
    [".form-control::-moz-placeholder" {:color "#ccc"
                                        :opacity 1}]; Mozilla Firefox 19+
    [".form-control:-ms-input-placeholder" {:color "#ccc"}]; Internet Explorer 10-11
    ))

(defn- generate-media-queries []
  (list
    (stylesheet/at-media {:max-width (u/px 480)}
                         (list
                           [(s/descendant :.rems-table.cart :tr)
                            {:border-bottom "none"}]
                           [(s/descendant :.logo :.img)
                            {:background [[(:color1 context/*theme*) "url(\"/img/Logo-matala.png\")" :center :center :no-repeat]]
                             :-webkit-background-size "contain"
                             :-moz-background-size "contain"
                             :-o-background-size "contain"
                             :background-size "contain"}]
                           [:.logo
                            {:height (u/px 150)}]))
    (stylesheet/at-media {:min-width (u/px 768)}
                         (list
                           [(s/descendant :.rems-table :td:before)
                            {:display "none"}]
                           [:.rems-table
                            [:th
                             :td
                             {:display "table-cell"}]]
                           [:.language-switcher
                            :.role-switcher
                            {:padding ".5em .5em"}]))
    (stylesheet/at-media {:min-width (u/px 480)}
                         [:.actions {:white-space "nowrap"}])))

(defn- generate-phase-styles []
  [:.phases {:width "100%"
             :height (u/px 40)
             :display "flex"
             :flex-direction "row"
             :justify-content "stretch"
             :align-items "center"}
   [:.phase {:background-color "#eee"
             :flex-grow 1
             :height (u/px 40)
             :display "flex"
             :flex-direction "row"
             :justify-content "stretch"
             :align-items "center"}
    [:span {:flex-grow 1
            :text-align "center"
            :min-width (u/px 100)}]
    [(s/& ":not(:last-of-type):after") {:content "\"\""
                                       :border-top [[(u/px 20) :solid :white]]
                                       :border-left [[(u/px 10) :solid :transparent]]
                                       :border-bottom [[(u/px 20) :solid :white]]
                                       :border-right "none"}]
    [(s/& ":first-of-type") {:border-top-left-radius (u/px 4)
                            :border-bottom-left-radius (u/px 4)}]
    [(s/& ":last-of-type") {:border-top-right-radius (u/px 4)
                           :border-bottom-right-radius (u/px 4)}]
    [(s/& ":not(:first-of-type):before") {:content "\"\""
                                         :border-top [[(u/px 20) :solid :transparent]]
                                         :border-left [[(u/px 10) :solid :white]]
                                         :border-bottom [[(u/px 20) :solid :transparent]]
                                         :border-right "none"}]
    [:&.active {:background-color (:color1 context/*theme*)
                :border-color (:color2 context/*theme*)
                :color "#000"}]
    [:&.completed {:background-color (:color2 context/*theme*)
                    :border-color (:color2 context/*theme*)
                    :color "#fff"}]]])

(defn- generate-rems-table-styles []
  (list
    [:.rems-table.cart {:background "#fff"
                        :color "#000"
                        :margin 0}
     [:tr {:border-bottom [[(u/px 1) :solid (:color1 context/*theme*)]]}]
     [:td:before {:content "initial"}]
     [:th
      :td:before
      {:color "#000"}]
     [:tr
      [(s/& (s/nth-child "2n")) {:background "#fff"}]]
     ]
    [:.rems-table {:margin "1em 0"
                   :min-width "100%"
                   :background-color (:color2 context/*theme*)
                   :color "#fff"
                   :border-radius (u/rem 0.4)
                   :overflow "hidden"}
     [:th {:display "none"}]
     [:td {:display "block"}
      [:&:before {:content "attr(data-th)\":\""
                  :font-weight "bold"
                  :margin-right (u/rem 0.5)
                  :display "inline-block"}]
      [:&:last-child:before {:content "attr(data-th)\"\""}]]
     [:th
      :td
      {:text-align "left"
       :padding "0.5em 1em"}]
     [:th
      :td:before
      {:color "#fff"}]
     [:tr {:margin "0 1rem"}
      [(s/& (s/nth-child "2n")) {:background-color "#8a9dca"}]]
     [:td.actions:last-child {:text-align "right"
                              :padding-right (u/rem 1)}]
     ]
    [:.inner-cart {:margin (u/em 1)}]
    [:.outer-cart {:border [[(u/px 1) :solid (:color1 context/*theme*)]]
                   :border-radius (u/rem 0.4)}]
    [:.cart-title {:margin-left (u/em 1)
                   :font-weight "bold"}]))

(defstyles screen
  (generate-at-font-faces)
  [:* {:margin "0"}]
  [:a
   :button
   {:cursor "pointer"}]
  [:a {:color (:color3 context/*theme*)}]
  [:html {:position "relative"
          :min-width (u/px 320)
          :height "100%"}]
  [:body {:font-family "'Lato', sans-serif"
          :min-height "100%"
          :display "flex"
          :flex-direction "column"
          :padding-top (u/px 56)}]
  [:.fixed-top {:background-color "#fff"
                :border-bottom [[(u/px 1) :solid (:color1 context/*theme*)]]
                :min-height (u/px 56)}]
  [:.main-content {:display "flex"
                   :flex-direction "column"
                   :flex-wrap "none"
                   :min-height (u/px 300)
                   :flex-grow "1"}]
  [:.container {:max-width (u/px 891)}]
  [:.btn-primary
   [:&:hover
    :&:focus
    :&:active:hover
    {:background-color "#d84f0e"
     :border-color (:color4 context/*theme*)
     :outline-color "transparent"}]
   {:background-color (:color4 context/*theme*)
    :border-color (:color4 context/*theme*)
    :outline-color "transparent"}]
  [:.btn-secondary
   [:&:hover
    :&:focus
    :&:active:hover
    {:outline-color "transparent"}]]
  [:.alert-info
   :state-info
   {:color (:color3 context/*theme*)
    :background-color (:color1 context/*theme*)}]
  [:.alert-success
   (s/descendant :.state-approved :.card-header)
   {:color "#3c763d"
    :background-color "#cee798"}]
  [:.alert-warning {:color "#6f572f"
                    :background-color "#e7d96f"}]
  [:.alert-danger
   (s/descendant :.state-rejected :.card-header)
   {:color "#79302f"
    :background-color "#e28b83"}]
  [:.nav-link
   :.btn-link
   (s/descendant :.nav-link :a)
   {:color (:color3 context/*theme*)
    :border 0 }] ;for button links
  [:.navbar
   [:.nav-link :.btn-link
    {:text-transform "uppercase"}]]
  [:.navbar-toggler {:border-color (:color1 context/*theme*)}]
  [:.nav-link
   :.btn-link
   [:&.active
    {:color (:color4 context/*theme*)}]
   [:&:hover
    {:color (:color4 context/*theme*)}]]
  [:.logo {:height (u/px 140)
           :background-color (:color1 context/*theme*)
           :padding "0 20px"
           :margin-bottom (u/em 1)}]
  [(s/descendant :.logo :.img) {:height "100%"
                                :background [[(:color1 context/*theme*) "url(\"/img/logo2.png\")" :left :center :no-repeat]]
                                :-webkit-background-size "contain"
                                :-moz-o-background-size "contain"
                                :-o-background-size "contain"
                                :background-size "contain"
                                :background-origin "content-box"
                                :padding-left (u/px 20)
                                :padding-right (u/px 20)}]
  [:footer {:width "100%"
            :height (u/px 53.6)
            :background-color (:color1 context/*theme*)
            :text-align "center"
            :margin-top (u/em 1)}]
  [:.jumbotron
   {:background-color "#fff"
    :text-align "center"
    :max-width (u/px 420)
    :margin "30px auto"
    :color "#000"
    :border-style "solid"
    :border-width (u/px 1)
    :box-shadow "0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19)"}
   [:h2 {:margin-bottom (u/px 20)}]]
  [:.login-btn {:max-height (u/px 70)}
   [:&:hover {:filter "brightness(80%)"}]]
  (generate-rems-table-styles)
  [:.btn.disabled {:opacity 0.25}]
  [:.catalogue-item-link {:color "#fff"
                          :text-decoration "underline"}]
  ;Has to be defined before the following media queries
  [:.language-switcher
   :.role-switcher
   {:padding ".5em 0"}]
  (generate-media-queries)
  [:.user
   :.language-switcher
   {:white-space "nowrap"}]
  [(s/descendant :.user :.nav-link) {:display "inline-block"}]
  [:.user-name {:text-transform "none"}]
  [:.fa
   :.user-name
   {:margin-right (u/px 5)}]
  [:.navbar {:padding-left 0
             :padding-right 0}]
  [(s/descendant :.navbar-text :.language-switcher)
   {:margin-right (u/rem 1)}]
  [:.example-page {:margin (u/rem 2)}]
  [(s/> :.example-page :h1) {:margin "4rem 0"}]
  [(s/> :.example-page :h2) {:margin-top (u/rem 8)
                             :margin-bottom (u/rem 2)}]
  [(s/> :.example-page :h3) {:margin-bottom (u/rem 1)}]
  [(s/descendant :.example-page :.example) {:margin-bottom (u/rem 4)}]
  [:.example-content {:border "1px dashed black"}]
  [:.example-content-end {:clear "both"}]
  [:form.inline
   :.form-actions.inline
   {:display "inline-block"}
   [:.btn-link
    {:border "none"
     :padding 0}]]
  [:.modal-title {:color "#292b2c"}]
  [(s/+
     (s/descendant :.language-switcher :form)
     :form)
   {:margin-left (u/rem 0.5)}]
  [(s/descendant :.role-switcher :form) {:margin-left (u/rem 0.5)}]
  [:.actions {:text-align "right"
              :padding "0 1rem"}]
  [:.navbar-flex {:display "flex"
                  :flex-direction "row"
                  :justify-content "space-between"
                  :min-width "100%"}
   [:nav {:flex 1}]]
  [(s/> :.form-actions "*:not(:first-child)")
   (s/> :.actions "*:not(:first-child)")
   {:margin-left (u/em 0.5)}]
  [:.full {:width "100%"}]
  [:.rectangle {:width (u/px 50)
                :height (u/px 50)}]
  [:.color-1 {:background-color (:color1 context/*theme*)}]
  [:.color-2 {:background-color (:color2 context/*theme*)}]
  [:.color-3 {:background-color (:color3 context/*theme*)}]
  [:.color-4 {:background-color (:color4 context/*theme*)}]
  [:.color-title {:padding-top (u/rem 0.8)}]
  [(s/descendant :.alert :ul ) {:margin-bottom 0}]
  [:ul.comments {:list-style-type "none"}]
  [:.inline-comment {:font-size (u/rem 1)}]
  [(s/& :p.inline-comment ":last-child") {:margin-bottom 0}]
  [:.inline-comment-content {:display "inline-block"}]
  [:.license-panel {:display "inline-block"
                    :width "inherit"}]
  [:.license-header
   [:&:after {:font-family "'FontAwesome'"
                            :float "right"
                            :content "\"\\f068\""}]
   [:&.collapsed
    [:&:after {:content "\"\\f067\""}]]]
  [:.card-header.clickable {:cursor "pointer"}]
  [(s/descendant :.card-header :a) {:color "inherit"}]
  ;hax for opening misalignment
  [:.license-title {:margin-top (u/px 3)}]
  [:.collapse-wrapper {:border-radius (u/rem 0.4)
                       :border "1px solid #ccc"}
   [:.clickable
    [:.card-title
     [(s/& ".collapsed:before") {:content "\"\\f067\""}]
     [:&:before {:font-family "'FontAwesome'"
                 :float "right"
                 :content "\"\\f068\""}]]]
   [:.card-header {:border-bottom "none"
                   :border-radius (u/rem 0.4)
                   :font-weight 500
                   :font-size (u/rem 1.5)
                   :line-height 1.1
                   :font-family "'Lato'"}]]
  [:.collapse-content {:padding (u/rem 1)}]
  (generate-phase-styles)
  [(s/descendant :.document :h3) {:margin-top (u/rem 4)}]
  ;These must be last as the parsing fails when the first non-standard element is met
  (generate-form-placeholder-styles))

(defn generate-css []
  (g/css {:pretty-print? false} screen))
