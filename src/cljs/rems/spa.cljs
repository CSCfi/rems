(ns rems.spa
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET]]
            [rems.actions :refer [actions-page fetch-actions]]
            [rems.ajax :refer [load-interceptors!]]
            [rems.application :refer [application-page fetch-application]]
            [rems.atoms :as atoms]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :refer [catalogue-page]]
            [rems.config :as config]
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

(defn home-page []
  (if (:user @(rf/subscribe [:identity]))
    [:p "Logged in."]
    [auth/login-component]))

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
        page-name "todo"]
    [:div
     [nav/navigation-widget page-name]
     [logo]
     ;;[:button {:on-click #(rf/dispatch [:set-active-page :catalogue])} "catalogue"]
     #_[:div.container message]
     [:div.container.main-content [content]]
     [footer]]))

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
  ;; TODO: a bit hacky:
  (rf/dispatch [:rems.application/fetch-application-result nil])
  (rf/dispatch [:rems.application/start-fetch-application id])
  (rf/dispatch [:set-active-page :application]))

(secretary/defroute "/application" {{items :items} :query-params}
  (rf/dispatch [:rems.application/fetch-application-result nil])
  (rf/dispatch [:rems.application/start-new-application (cart/parse-items items)])
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

(defn set-identity!
  "Receives as a parameter following kind of structure:
   {:user {:eppn \"\"eppn\" \"developer\"
           :email \"developer@e.mail\"
           :displayName \"deve\"
           :surname \"loper\"
           ...}
    :roles [\"applicant\" \"approver\"]}
    Roles are converted to clojure keywords inside the function before dispatching"
  [user-and-roles]
  (let [user-and-roles (js->clj user-and-roles :keywordize-keys true)]
    (rf/dispatch-sync [:set-identity (if (:user user-and-roles)
                                   (update user-and-roles :roles #(mapv keyword (:roles user-and-roles)))
                                   user-and-roles)])))

(defn dispatch-initial-route! [href]
  (secretary/dispatch! href))

(defn fetch-translations! []
  (GET "/api/translations" {:handler #(rf/dispatch [:loaded-translations %])
                            :response-format :json
                            :keywords? true}))

(defn fetch-theme! []
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
  (fetch-theme!)
  (config/fetch-config!)
  (hook-browser-navigation!)
  (mount-components)
  (dispatch-initial-route! (.. js/window -location -href)))
