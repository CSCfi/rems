(ns rems.navbar
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.ajax]
            [rems.atoms :as atoms]
            [rems.common.util :refer [getx]]
            [rems.common.roles :as roles]
            [rems.config]
            [rems.globals]
            [rems.guide-util :refer [component-info example]]
            [rems.language-switcher :refer [language-switcher]]
            [rems.text :refer [text]]
            [rems.theme]))

(defn- nav-link-impl [{:keys [path title active? aria-label]}]
  [atoms/link
   (merge {:class (str "nav-link" (if active? " active" ""))}
          (when aria-label
            {:aria-label aria-label})
          (when (rems.ajax/local-uri? {:uri path})
            {:data-toggle "collapse"
             :data-target ".navbar-collapse.show"}))
   path
   title])

(defn nav-link
  "A link to path that is shown as active when the current browser location matches the path.

   By default checks if path is a prefix of location, but if match-mode is :exact,
   checks that path is exactly location."
  ([path title & [match-mode]]
   (nav-link {:path path :title title :match-mode match-mode}))
  ([{:keys [path title aria-label match-mode]}]
   (let [location @(rf/subscribe [:path])
         active? (case match-mode
                   :exact
                   (= location path)
                   ;; default: prefix
                   (str/starts-with? location path))]
     [nav-link-impl
      {:path path
       :title title
       :active? active?
       :aria-label aria-label}])))

(defn user-widget []
  (when-some [user @rems.globals/user]
    [:div.user-widget.px-2.px-sm-0
     [nav-link {:path "/profile"
                :title [:span
                        [:i.fa.fa-user.mr-1]
                        [:span.icon-description (:name user)]]
                :aria-label (str (text :t.navigation/profile) ": " (:name user))}]
     [atoms/link {:id "logout" :class "nav-link"
                  :aria-label (text :t.navigation/logout)}
      "/logout"
      [:span
       [:i.fa.fa-sign-out-alt.mr-1]
       [:span.icon-description (text :t.navigation/logout)]]]]))

(defn- extra-page-link [page]
  (let [lang @rems.config/language-or-default
        url (or (get-in page [:translations lang :url])
                (page :url)
                (str "/extra-pages/" (page :id)))
        text (get-in page [:translations lang :title] (text :t/missing))]
    [nav-link url text]))

(defn- extra-pages-container [{:keys [context include?]}]
  (when-let [extra-pages (:extra-pages @rems.globals/config)]
      (into [:<>]
            (for [page (filter include? extra-pages)]
              ^{:key (str context (getx page :id))}
            [extra-page-link page]))))

(defn navbar-extra-pages []
  [extra-pages-container {:context "navbar-extra-pages" :include? #(:show-menu % true)}])

(defn footer-extra-pages []
  [extra-pages-container {:context "footer-extra-pages" :include? #(:show-footer % false)}])

(defn navbar-items [e attrs identity]
  (let [roles (:roles identity)
        config @(rf/subscribe [:rems.config/config])
        catalogue-is-public (:catalogue-is-public config)]
    [e attrs [:div.navbar-nav.mr-auto
              (when-not (:user identity)
                [nav-link "/" (text :t.navigation/home) :exact])
              (when (or (roles/is-logged-in? roles) catalogue-is-public)
                [nav-link "/catalogue" (text :t.navigation/catalogue)])
              (when (roles/show-applications? roles)
                [nav-link "/applications" (text :t.navigation/applications)])
              (when (some roles/+handling-roles+ roles)
                [nav-link "/actions" (text :t.navigation/actions)])
              (when (roles/show-admin-pages? roles)
                [nav-link "/administration" (text :t.navigation/administration)])
              [navbar-extra-pages]]
     [language-switcher]]))

(defn navbar-normal [identity]
  [:nav.navbar-flex {:aria-label (text :t.navigation/navigation)}
   [:div.navbar.navbar-expand-sm.flex-fill
    [:button.navbar-toggler
     {:type :button :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    (when (rems.theme/use-navbar-logo?)
      [:div.navbar-brand.logo-menu
       [:div.img]])
    [navbar-items :div#big-navbar.collapse.navbar-collapse.mr-3 {} identity]]
   [:div.navbar [user-widget (:user identity)]]])

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
            [nav-link-impl {:path "example/path" :title "Link text"}])
   (example "nav-link active"
            [nav-link-impl {:path "example/path" :title "Link text" :active? true}])])
