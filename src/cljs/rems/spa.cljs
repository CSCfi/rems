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
            [rems.atoms :as atoms]
            [rems.catalogue :refer [catalogue-page]]
            [rems.guide-page :refer [guide-page]]
            [rems.handlers]
            [rems.navbar :as nav]
            [rems.subscriptions]
            [rems.text :refer [text]])
  (:import goog.History))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "TODO about page in markdown"]]])

(defn login []
  [:div.m-auto.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   [atoms/link-to {} (str (:root-path nav/context) "/login") [atoms/image {:class "login-btn"} "/img/haka-logo.jpg"]]])

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

(defn footer []
  [:footer.footer
   [:div.container [:nav.navbar [:div.navbar-text (text :t/footer)]]]])

(defn logo []
  [:div.logo [:div.container.img]])

(defn page []
  (let [page-id @(rf/subscribe [:page])
        content (pages page-id)
        page-name "todo"
        user @(rf/subscribe [:user])]
    [:div
     [:div.fixed-top
      [:div.container
       [nav/navbar-normal page-name user]
       [nav/navbar-small page-name user]]]
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
