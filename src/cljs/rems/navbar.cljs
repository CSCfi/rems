(ns rems.navbar
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.language-switcher :refer [language-switcher]]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO fetch as a subscription?
(def context {:root-path ""})

;; TODO these role functions should be placed somewhere else
(defn when-role [role & content]
  (let [active-role (rf/subscribe [:active-role])]
    (when (= @active-role role)
      content)))

(defn when-roles [roles & content]
  (let [active-role (rf/subscribe [:active-role])]
    (when (contains? roles @active-role)
      content)))

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

(defn navbar-items [e page-name user]
  [e
   [:div.navbar-nav.mr-auto
    (if user
      (list
       (when-role :applicant
         [nav-link "/catalogue" (text :t.navigation/catalogue) (= page-name "catalogue")])
       (when-role :applicant
         [nav-link "/applications" (text :t.navigation/applications) (= page-name "applications")])
       (when-roles #{:approver :reviewer}
         [nav-link "/approvals" (text :t.navigation/approvals) (= page-name "approvals")]))
      [nav-link "#/" (text :t.navigation/home) (= page-name "home")])
    [nav-link "#/about" (text :t.navigation/about) (= page-name "about")]]
   [language-switcher]])

(defn navbar-normal
  [page-name user]
  [:div.navbar-flex
   [:nav.navbar.navbar-expand-sm {:role "navigation"}
    [:button.navbar-toggler
     {:type "button" :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse page-name user]]
   [:div.navbar [user-widget user]]])

(defn navbar-small
  [page-name user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-name user])

(defn guide []
  [:div
   (component-info nav-link)
   (example "nav-link"
            [nav-link "example/path" "link text"])
   (example "nav-link active"
            [nav-link "example/path" "link text" "page-name" "page-name"])])
