(ns rems.spa
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [rems.actions :refer [actions-page fetch-actions]]
            [rems.ajax :refer [load-interceptors!]]
            [rems.application :refer [application-page fetch-application]]
            [rems.catalogue :refer [catalogue-page]]
            [rems.guide-page :refer [guide-page]]
            [rems.handlers]
            [rems.language-switcher :refer [language-switcher]]
            [rems.subscriptions]
            [rems.text :refer [text]])
  (:import goog.History))

(def context {:root-path ""})

(defn when-role [role & content]
  (let [active-role (rf/subscribe [:active-role])]
    (when (= @active-role role)
      content)))

(defn when-roles [roles & content]
  (let [active-role (rf/subscribe [:active-role])]
    (when (contains? roles @active-role)
      content)))

(defn link-to [opts uri title]
  [:a (merge opts {:href uri}) title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn url-dest
  [dest]
  (str (:root-path context) dest))

(defn nav-link [path title & [active?]]
  [link-to {:class (str "nav-item nav-link" (if active? " active" ""))} (url-dest path) title])

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
   (language-switcher)])

(defn navbar-normal
  [page-name user]
  [:div.navbar-flex
   [:nav.navbar.navbar-toggleable-sm {:role "navigation"}
    [:button.navbar-toggler
     {:type "button" :data-toggle "collapse" :data-target "#small-navbar"}
     "\u2630"]
    [navbar-items :div#big-navbar.collapse.navbar-collapse page-name user]]])

(defn navbar-small
  [page-name user]
  [navbar-items :div#small-navbar.collapse.navbar-collapse.collapse.hidden-md-up page-name user])

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "TODO about page in markdown"]]])

(defn login []
  [:div.m-auto.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to {} (str (:root-path context) "/Shibboleth.sso/Login") [image {:class "login-btn"} "/img/haka-logo.jpg"])])

(defn home-page []
  (if @(rf/subscribe [:user])
    [:p "Logged in."]
    [login]))

(def pages
  {:home home-page
   :catalogue catalogue-page
   :guide guide-page
   :about about-page
   :actions actions-page
   :application application-page})

(defn user-switcher [user]
  (let [user (rf/subscribe [:user])]
    (when @user
      [:div.user.px-2.px-sm-0
       [:i.fa.fa-user]
       [:span.user-name (str (get-in @user "eppn") " /")]
       [link-to {:class (str "px-0 nav-link")} (url-dest "/Shibboleth.sso/Logout?return=%2F") (text :t.navigation/logout)]])))

(defn footer []
  [:footer.footer
   [:div.container [:nav.navbar [:div.navbar-text (text :t/footer)]]]])

(defn logo []
  [:div.logo [:div.container.img]])

(defn page []
  (let [page-id @(rf/subscribe [:page])
        content (pages page-id)
        page-name "todo"
        user {:todo? true }]
    [:div
     [:div.fixed-top
      [:div.container
       [navbar-normal page-name user]
       [navbar-small page-name user]]]
     [logo]
     ;;[:button {:on-click #(rf/dispatch [:set-active-page :catalogue])} "catalogue"]
     #_[:div.container message]
     [:div.container.main-content [content]]
     [footer]
     ]))

;; -------------------------
;; Routes

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

(secretary/defroute "/catalogue" []
  (rf/dispatch [:set-active-page :catalogue]))

(secretary/defroute "/guide" []
  (rf/dispatch [:set-active-page :guide]))

(secretary/defroute "/about" []
  (rf/dispatch [:set-active-page :about]))

(secretary/defroute "/actions" []
  (rf/dispatch [:set-active-page :actions]))

(secretary/defroute "/application/:id" {id :id}
  (rf/dispatch [:rems.application/start-fetch-application id])
  (rf/dispatch [:set-active-page :application]))


;; -------------------------
;; History
;; must be called after routes have been defined

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn set-user! [user]
  (rf/dispatch-sync [:set-user (js->clj user :keywordize-keys true)]))

(defn dispatch-initial-route! [href]
  (secretary/dispatch! href))

(defn fetch-translations! []
  (GET "/api/translations" {:handler #(rf/dispatch [:loaded-translations %])
                            :response-format :json
                            :keywords? true}))

(defn fetch-theme []
  (GET "/api/theme" {:handler #(rf/dispatch [:loaded-theme %])
                     :response-format :json
                     :keywords? true}))

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (fetch-translations!)
  (fetch-theme)
  (hook-browser-navigation!)
  (mount-components)
  (dispatch-initial-route! (.. js/window -location -href)))
