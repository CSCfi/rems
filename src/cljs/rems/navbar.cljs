(ns rems.navbar
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.language-switcher :refer [language-switcher]]
            [rems.roles :as roles]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO fetch as a subscription?
(def context {:root-path ""})

(defn url-dest
  [dest]
  (str (:root-path context) dest))

(defn nav-link [path title & [active?]]
  [atoms/link-to {:class (str "nav-item nav-link" (if active? " active" ""))} (url-dest path) title])

(defn user-widget [user]
  (when user
    [:div.user.px-2.px-sm-0
     [:i.fa.fa-user]
     [:span.user-name (str (:commonName user) " /")]
     [atoms/link-to {:id "logout", :class (str "px-0 nav-link")} (url-dest "/logout") (text :t.navigation/logout)]]))

(defn navbar-extra-pages [e page-id identity]
  (let [config @(rf/subscribe [:rems.config/config])
        extra-pages (when config (config :extra-pages))
        language @(rf/subscribe [:language])]
    (when extra-pages
      (for [page extra-pages]
        (let [url (or (page :url)
                      (str "/#/extra-pages/" (page :id)))
              text (get-in page [:translations language :title] (text :t/missing))]
          [nav-link url text (= page-id :markdown)])))))

(defn navbar-items [e page-id identity]
  ;;TODO: get navigation options from subscription
  (let [roles (:roles identity)]
    [e [:div.navbar-nav.mr-auto
        (when (roles/is-logged-in? roles)
          [nav-link "#/catalogue" (text :t.navigation/catalogue) (= page-id :catalogue)])
        (when (roles/show-applications? roles)
          [nav-link "#/applications" (text :t.navigation/applications)
           (contains? #{:application :applications} page-id)])
        (when (roles/show-reviews? roles)
          [nav-link "#/actions" (text :t.navigation/actions) (= page-id :actions)])
        (when (roles/show-admin-pages? roles)
          [nav-link "#/administration"
           (text :t.navigation/administration)
           (and page-id (namespace page-id) (str/starts-with? (namespace page-id) "rems.administration"))])
        (when-not (:user identity) [nav-link "#/" (text :t.navigation/home) (= page-id :home)])
        (navbar-extra-pages e page-id identity)]
     [language-switcher]]))

(defn navbar-normal [page-id identity]
  [:div.navbar-flex
   [:nav.navbar.navbar-expand-sm {:role "navigation"}
    [:button.navbar-toggler
     {:type "button" :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse.mr-3 page-id identity]]
   [:div.navbar [user-widget (:user identity)]]])

(defn navbar-small [page-id user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-id user])

(defn navigation-widget [page-id]
  (let [identity @(rf/subscribe [:identity])]
    [:div.fixed-top
     [:div.navbar-top-bar [:div.navbar-top-left] [:div.navbar-top-right]]
     [:div.container
      [navbar-normal page-id identity]
      [navbar-small page-id identity]]
     [:div.navbar-bottom-bar]]))

(defn guide []
  [:div
   (component-info nav-link)
   (example "nav-link"
            [nav-link "example/path" "link text"])
   (example "nav-link active"
            [nav-link "example/path" "link text" "page-name" "page-name"])])
