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

(defn- nav-link-impl [path title & [active?]]
  [atoms/link {:class (str "nav-link" (if active? " active" ""))} (url-dest path) title])

(defn nav-link
  "A link to path that is shown as active when the current browser location matches the path.

   By default checks if path is a prefix of location, but if match-mode is :exact,
   checks that path is exactly location."
  [path title & [match-mode]]
  (let [location @(rf/subscribe [:path])
        active? (case match-mode
                  :exact
                  (= location path)
                  ;; default: prefix
                  (str/starts-with? location path))]
    [nav-link-impl path title active?]))

(defn user-widget [user]
  (when user
    [:div.user-widget.px-2.px-sm-0
     [:span
      [:i.fa.fa-user.mr-1]
      [:span.user-name (:name user)]]
     [atoms/link {:id "settings", :class "nav-link"} (url-dest "/settings")
      [:span {:aria-label (text :t.navigation/settings)}
       [:i.fa.fa-cog.mr-1]
       [:span.icon-description (text :t.navigation/settings)]]]
     [atoms/link {:id "logout", :class "nav-link"} (url-dest "/logout")
      [:span {:aria-label (text :t.navigation/logout)}
       [:i.fa.fa-sign-out-alt.mr-1]
       [:span.icon-description (text :t.navigation/logout)]]]]))

(defn navbar-extra-pages [page-id]
  (let [config @(rf/subscribe [:rems.config/config])
        extra-pages (when config (config :extra-pages))
        language @(rf/subscribe [:language])
        extra-page-id @(rf/subscribe [:rems.extra-pages/page-id])]
    (when extra-pages
      (for [page extra-pages]
        (let [url (or (page :url)
                      (str "/extra-pages/" (page :id)))
              text (get-in page [:translations language :title] (text :t/missing))]
          [nav-link url text])))))

(defn navbar-items [e page-id identity]
  ;;TODO: get navigation options from subscription
  (let [roles (:roles identity)]
    [e (into [:div.navbar-nav.mr-auto
              (when (roles/is-logged-in? roles)
                [nav-link "/catalogue" (text :t.navigation/catalogue)])
              (when (roles/show-applications? roles)
                [nav-link "/applications" (text :t.navigation/applications)])
              (when (roles/show-reviews? roles)
                [nav-link "/actions" (text :t.navigation/actions)])
              (when (roles/show-admin-pages? roles)
                [nav-link "/administration" (text :t.navigation/administration)])
              (when-not (:user identity)
                [nav-link "/" (text :t.navigation/home) :exact])]
             (navbar-extra-pages page-id))
     [language-switcher]]))

(defn navbar-normal [page-id identity]
  [:nav.navbar-flex
   [:div.navbar.navbar-expand-sm.flex-fill
    [:button.navbar-toggler
     {:type :button :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse.mr-3 page-id identity]]
   [:div.navbar [user-widget (:user identity)]]])

(defn navbar-small [page-id user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-id user])

(defn skip-navigation []
  [:a.skip-navigation
   {:href "#main-content"}
   (text :t.navigation/skip-navigation)])

(defn navigation-widget [page-id]
  (let [identity @(rf/subscribe [:identity])]
    [:div.fixed-top
     [skip-navigation]
     [:div.navbar-top-bar [:div.navbar-top-left] [:div.navbar-top-right]]
     [:div.navbar-wrapper.container-fluid
      [navbar-normal page-id identity]
      [navbar-small page-id identity]]
     [:div.navbar-bottom-bar]]))

(defn guide []
  [:div
   (component-info nav-link)
   [:p "Here are examples of what the inactive and active nav-links look like."
    "The examples use nav-link-impl because we can't fake the :path subscription."]
   (example "nav-link inactive"
            [nav-link-impl "example/path" "Link text" false])
   (example "nav-link active"
            [nav-link-impl "example/path" "Link text" true])])
