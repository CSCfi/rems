(ns rems.spa
  (:require [reagent.core :as r]
            [re-frame.core :as rf :refer [dispatch reg-event-db reg-event-fx reg-sub]]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET]]
            [rems.actions :refer [actions-page fetch-actions]]
            [rems.administration :refer [administration-page]]
            [rems.ajax :refer [load-interceptors!]]
            [rems.application :refer [application-page fetch-application]]
            [rems.applications :refer [applications-page]]
            [rems.atoms :as atoms]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :refer [catalogue-page]]
            [rems.config :as config]
            [rems.guide-page :refer [guide-page]]
            [rems.navbar :as nav]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch!]])
  (:import goog.History))

;;; subscriptions

(reg-sub
 :page
 (fn [db _]
   (:page db)))

(reg-sub
 :docs
 (fn [db _]
   (:docs db)))

;; TODO: possibly move translations out
(reg-sub
 :translations
 (fn [db _]
   (:translations db)))

(reg-sub
 :language
 (fn [db _]
   (:language db)))

;; TODO: possibly move theme out
(reg-sub
 :theme
 (fn [db _]
   (:theme db)))

(reg-sub
 :identity
 (fn [db _]
   (:identity db)))

(reg-sub
 :user
 (fn [db _]
   (get-in db [:identity :user])))

(reg-sub
 :roles
 (fn [db _]
   (get-in db [:identity :roles])))

;;; handlers

(reg-event-db
 :initialize-db
 (fn [_ _]
   {:page :home
    :language :en
    :translations {}
    :identity {:user nil :roles nil}}))

(reg-event-db
 :set-active-page
 (fn [db [_ page]]
   (assoc db :page page)))

(reg-event-db
 :set-docs
 (fn [db [_ docs]]
   (assoc db :docs docs)))

(reg-event-db
 :loaded-translations
 (fn [db [_ translations]]
   (assoc db :translations translations)))

(reg-event-db
 :loaded-theme
 (fn [db [_ theme]]
   (assoc db :theme theme)))

(reg-event-db
 :set-identity
 (fn [db [_ identity]]
   (assoc db :identity identity)))

(reg-event-db
 :set-current-language
 (fn [db [_ language]]
   (assoc db :language language)))

(reg-event-fx
 :landing-page-redirect!
 (fn [{:keys [db]}]
   ;; do we have the roles set by set-identity already?
   (if (get-in db [:identity :roles])
     (let [roles (set (get-in db [:identity :roles]))]
       (println "Selecting landing page based on roles" roles)
       (.removeItem js/window.sessionStorage "rems-redirect-url")
       (cond
         (contains? roles :owner) (dispatch! "/#/administration")
         (contains? roles :approver) (dispatch! "/#/actions")
         (contains? roles :reviewer) (dispatch! "/#/actions")
         :else (dispatch! "/#/catalogue"))
       {})
      ;;; else dispatch the same event again while waiting for set-identity (happens especially with Firefox)
     {:dispatch [:landing-page-redirect!]})))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     (text :t.about/text)]]])

(defn home-page []
  (if @(rf/subscribe [:user])
    (do (rf/dispatch [:landing-page-redirect!]) ; don't show the empty page
        [:div])
    [auth/login-component]))

(defn not-found-page[]
  [:h3 "No such page."])

(def pages
  {:home home-page
   :catalogue catalogue-page
   :guide guide-page
   :about about-page
   :actions actions-page
   :application application-page
   :applications applications-page
   :administration administration-page
   :not-found not-found-page})

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
     [:div.container.main-content [content]]
     [footer]]))

;; -------------------------
;; Routes

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

(secretary/defroute "/landing_page" []
  (if-let [url (.getItem js/sessionStorage "rems-redirect-url")]
    (do
      (println "Redirecting to" url "after authorization")
      (.removeItem js/window.sessionStorage "rems-redirect-url")
      (dispatch! url))
    (rf/dispatch [:landing-page-redirect!])))

(secretary/defroute "/catalogue" []
  (rf/dispatch [:set-active-page :catalogue]))

(secretary/defroute "/guide" []
  (rf/dispatch [:set-active-page :guide]))

(secretary/defroute "/about" []
  (rf/dispatch [:set-active-page :about]))

(secretary/defroute "/actions" []
  (rf/dispatch [:set-active-page :actions]))

(secretary/defroute "/application/:id" {id :id}
  (rf/dispatch [:rems.application/zero-state])
  (rf/dispatch [:rems.application/start-fetch-application id])
  (rf/dispatch [:rems.application/start-fetch-potential-third-party-reviewers])
  (rf/dispatch [:set-active-page :application]))

(secretary/defroute "/application" {{items :items} :query-params}
  (rf/dispatch [:rems.application/zero-state])
  (rf/dispatch [:rems.application/start-new-application (cart/parse-items items)])
  (rf/dispatch [:set-active-page :application]))

(secretary/defroute "/applications" []
  (rf/dispatch [:set-active-page :applications]))

(secretary/defroute "/administration" []
  (rf/dispatch [:set-active-page :administration]))

(secretary/defroute "*" []
  (rf/dispatch [:set-active-page :not-found]))

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
  (mount-components))
