(ns rems.navbar
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.language-switcher :refer [language-switcher]]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO fetch as a subscription?
(def context {:root-path ""})

;; TODO consider moving when-role as it's own component
(defn when-roles [roles current-roles content]
  (when (some roles current-roles)
    content))

(defn when-role [role current-roles content]
  (when-roles #{role} current-roles content))

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
     [atoms/link-to {:class (str "px-0 nav-link")} (url-dest "/logout") (text :t.navigation/logout)]]))

(defn navbar-items [e page-name identity]
  ;;TODO: get navigation options from subscription
  (let [current-roles (:roles identity)]
    (prn :ROLES current-roles)
    [e [:div.navbar-nav.mr-auto
        (when-role :applicant current-roles
                   [nav-link "#/catalogue" (text :t.navigation/catalogue) (= page-name "catalogue")])
        (when-role :applicant current-roles
                   [nav-link "#/applications" (text :t.navigation/applications) (= page-name "applications")])
        (when-roles #{:approver :reviewer} current-roles
                    [nav-link "#/actions" (text :t.navigation/actions) (= page-name "actions")])
        (when-role :owner current-roles
                   [nav-link "#/administration" (text :t.navigation/administration) (= page-name "administration")])
        (when-not (:user identity) [nav-link "#/" (text :t.navigation/home) (= page-name "home")])
        [nav-link "#/about" (text :t.navigation/about) (= page-name "about")]]
     [language-switcher]]))

(defn navbar-normal
  [page-name identity]
  [:div.navbar-flex
   [:nav.navbar.navbar-expand-sm {:role "navigation"}
    [:button.navbar-toggler
     {:type "button" :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse page-name identity]]
   [:div.navbar [user-widget (:user identity)]]])

(defn navbar-small
  [page-name user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-name user])

(defn navigation-widget [page-name]
  (let [identity @(rf/subscribe [:identity])]
    [:div.fixed-top
     [:div.container
      [navbar-normal page-name identity]
      [navbar-small page-name identity]]]))

(defn guide []
  [:div
   (component-info nav-link)
   (example "nav-link"
            [nav-link "example/path" "link text"])
   (example "nav-link active"
            [nav-link "example/path" "link text" "page-name" "page-name"])])
