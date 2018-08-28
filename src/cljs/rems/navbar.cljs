(ns rems.navbar
  (:require [re-frame.core :as rf]
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

(defn navbar-items [e page-id identity]
  ;;TODO: get navigation options from subscription
  (let [current-roles (:roles identity)]
    [e [:div.navbar-nav.mr-auto
        (when-role :applicant current-roles
                   [nav-link "#/catalogue" (text :t.navigation/catalogue) (= page-id :catalogue)])
        (when-role :applicant current-roles
                   [nav-link "#/applications" (text :t.navigation/applications) (contains? #{:application
                                                                                             :applications}
                                                                                           page-id)])
        (when-roles #{:approver :reviewer} current-roles
                    [nav-link "#/actions" (text :t.navigation/actions) (= page-id :actions)])
        (when-role :owner current-roles
                   [nav-link "#/administration" (text :t.navigation/administration) (contains? #{:administration
                                                                                                 :create-license
                                                                                                 :create-resource
                                                                                                 :create-workflow
                                                                                                 :create-catalogue-item}
                                                                                               page-id)])
        (when-not (:user identity) [nav-link "#/" (text :t.navigation/home) (= page-id :home)])
        [nav-link "#/about" (text :t.navigation/about) (= page-id :about)]]
     [language-switcher]]))

(defn navbar-normal [page-id identity]
  [:div.navbar-flex
   [:nav.navbar.navbar-expand-sm {:role "navigation"}
    [:button.navbar-toggler
     {:type "button" :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse page-id identity]]
   [:div.navbar [user-widget (:user identity)]]])

(defn navbar-small [page-id user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-id user])

(defn navigation-widget [page-id]
  (let [identity @(rf/subscribe [:identity])]
    [:div.fixed-top
     [:div.container
      [navbar-normal page-id identity]
      [navbar-small page-id identity]]]))

(defn guide []
  [:div
   (component-info nav-link)
   (example "nav-link"
            [nav-link "example/path" "link text"])
   (example "nav-link active"
            [nav-link "example/path" "link text" "page-name" "page-name"])])
