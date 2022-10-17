(ns rems.navbar
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.ajax]
            [rems.atoms :as atoms]
            [rems.common.roles :as roles]
            [rems.guide-util :refer [component-info example]]
            [rems.language-switcher :refer [language-switcher]]
            [rems.text :refer [text]]))

(defn- nav-link-impl [path title & [active?]]
  [atoms/link
   (merge {:class (str "nav-link" (if active? " active" ""))}
          (when (rems.ajax/local-uri? {:uri path})
            {:data-toggle "collapse"
             :data-target ".navbar-collapse.show"}))
   path
   title])

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
     [nav-link
      "/profile"
      [:span {:aria-label (text :t.navigation/profile)}
       [:i.fa.fa-user.mr-1]
       [:span.icon-description (:name user)]]]
     [atoms/link {:id "logout" :class "nav-link"} "/logout"
      [:span {:aria-label (text :t.navigation/logout)}
       [:i.fa.fa-sign-out-alt.mr-1]
       [:span.icon-description (text :t.navigation/logout)]]]]))

(defn navbar-extra-pages []
  (let [config @(rf/subscribe [:rems.config/config])
        extra-pages (when config (config :extra-pages))
        language @(rf/subscribe [:language])]
    (when extra-pages
      (doall (for [page extra-pages
                   :when (:show-menu page true)]
               (let [url (or (page :url)
                             (str "/extra-pages/" (page :id)))
                     text (get-in page [:translations language :title] (text :t/missing))]
                 ^{:key (str "navbar-extra-page-" (page :id))}
                 [nav-link url text]))))))

(defn footer-extra-pages []
  (let [config @(rf/subscribe [:rems.config/config])
        extra-pages (when config (config :extra-pages))
        language @(rf/subscribe [:language])]
    (when extra-pages
      (doall (for [page extra-pages
                   :when (:show-footer page false)]
               (let [url (or (page :url)
                             (str "/extra-pages/" (page :id)))
                     text (get-in page [:translations language :title] (text :t/missing))]
                 ^{:key (str "footer-extra-page-" (page :id))}
                 [nav-link url text]))))))

(defn navbar-items [e attrs identity]
  ;;TODO: get navigation options from subscription
  (let [roles (:roles identity)
        config @(rf/subscribe [:rems.config/config])
        catalogue-is-public (:catalogue-is-public config)]
    [e attrs (into [:div.navbar-nav.mr-auto
                    (when-not (:user identity)
                      [nav-link "/" (text :t.navigation/home) :exact])
                    (when (or (roles/is-logged-in? roles) catalogue-is-public)
                      [nav-link "/catalogue" (text :t.navigation/catalogue)])
                    (when (roles/show-applications? roles)
                      [nav-link "/applications" (text :t.navigation/applications)])
                    (when (roles/show-reviews? roles)
                      [nav-link "/actions" (text :t.navigation/actions)])
                    (when (roles/show-admin-pages? roles)
                      [nav-link "/administration" (text :t.navigation/administration)])]
                   (navbar-extra-pages))
     [language-switcher]]))

(defn navbar-normal [identity]
  (let [theme @(rf/subscribe [:theme])
        lang @(rf/subscribe [:language])]
    [:nav.navbar-flex {:aria-label (text :t.navigation/navigation)}
     [:div.navbar.navbar-expand-sm.flex-fill
      [:button.navbar-toggler
       {:type :button :data-toggle "collapse" :data-target "#small-navbar"}
       "\u2630"]
      (when (or ((keyword (str "navbar-logo-name-" (name lang))) theme)
                (:navbar-logo-name theme))
        [atoms/logo-navigation])
      [navbar-items :div#big-navbar.collapse.navbar-collapse.mr-3 {} identity]]
     [:div.navbar [user-widget (:user identity)]]]))

(defn navbar-small [user]
  [navbar-items :nav#small-navbar.collapse.navbar-collapse.hidden-md-up {:aria-label (text :t.navigation/navigation-small)} user])

(defn skip-navigation []
  [:a.skip-navigation
   {:href "#main-content"}
   (text :t.navigation/skip-navigation)])

(defn navigation-widget []
  (let [identity @(rf/subscribe [:identity])]
    [:div.fixed-top
     [skip-navigation]
     [:div.navbar-top-bar [:div.navbar-top-left] [:div.navbar-top-right]]
     [:div.navbar-wrapper.container-fluid
      [navbar-normal identity]
      [navbar-small identity]]
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
