(ns rems.spa
  (:require [clojure.string :as str]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [re-frame.core :as rf :refer [dispatch reg-event-db reg-event-fx reg-sub reg-fx]]
            [reagent.core :as r]
            [rems.actions :refer [actions-page]]
            [rems.actions.accept-invitation :refer [accept-invitation-page]]
            [rems.administration.administration :refer [administration-page]]
            [rems.administration.catalogue-item :refer [catalogue-item-page]]
            [rems.administration.catalogue-items :refer [catalogue-items-page]]
            [rems.administration.create-catalogue-item :refer [create-catalogue-item-page]]
            [rems.administration.create-form :refer [create-form-page]]
            [rems.administration.create-license :refer [create-license-page]]
            [rems.administration.create-resource :refer [create-resource-page]]
            [rems.administration.create-workflow :refer [create-workflow-page]]
            [rems.administration.form :refer [form-page]]
            [rems.administration.forms :refer [forms-page]]
            [rems.administration.license :refer [license-page]]
            [rems.administration.licenses :refer [licenses-page]]
            [rems.administration.resource :refer [resource-page]]
            [rems.administration.resources :refer [resources-page]]
            [rems.administration.workflow :refer [workflow-page]]
            [rems.administration.workflows :refer [workflows-page]]
            [rems.ajax :refer [load-interceptors!]]
            [rems.application :refer [application-page]]
            [rems.applications :refer [applications-page]]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :refer [catalogue-page]]
            [rems.config :as config]
            [rems.guide-page :refer [guide-page]]
            [rems.extra-pages :refer [extra-pages]]
            [rems.navbar :as nav]
            [rems.new-application :refer [new-application-page]]
            [rems.roles :as roles]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch parse-int]]
            [secretary.core :as secretary])
  (:require-macros [rems.read-gitlog :refer [read-current-version]])
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

(reg-sub
 :languages
 (fn [db _]
   (:languages db)))

(reg-sub
 :default-language
 (fn [db _]
   (:default-language db)))

;; TODO: possibly move theme out
(reg-sub
 :theme
 (fn [db _]
   (:theme db)))

;;; handlers

(reg-event-db
 :initialize-db
 (fn [_ _]
   {:page :home
    :language :en
    :languages [:en]
    :default-language :en
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

(reg-event-fx
 :set-current-language
 (fn [{:keys [db]} [_ language]]
   {:db (assoc db :language language)
    :update-document-language (name language)}))

(reg-fx
 :update-document-language
 (fn [language]
   (let [localized-css (str "/css/" (name language) "/screen.css")]
     (set! (.. js/document -documentElement -lang) language)
     (set! (.. js/document -title) (text :t.header/title))
     ;; Figwheel replaces the linked stylesheet
     ;; so we need to search dynamically
     (doseq [element (array-seq (.getElementsByTagName js/document "link"))]
       (when (str/includes? (.-href element) "screen.css")
         (set! (.-href element) localized-css))))))

(reg-event-fx
 :unauthorized!
 (fn [_ [_ current-url]]
   (println "Received unauthorized from" current-url)
   (.setItem js/sessionStorage "rems-redirect-url" current-url)
   (dispatch! "/")
   {}))

(reg-event-fx
 :forbidden!
 (fn [_ [_ current-url]]
   (println "Received forbidden from" current-url)
   {:dispatch [:set-active-page :forbidden]}))

(reg-event-fx
 :landing-page-redirect!
 (fn [{:keys [db]}]
   ;; do we have the roles set by set-identity already?
   (if (get-in db [:identity :roles])
     (let [roles (get-in db [:identity :roles])]
       (println "Selecting landing page based on roles" roles)
       (.removeItem js/sessionStorage "rems-redirect-url")
       (cond
         (roles/show-admin-pages? roles) (dispatch! "/#/administration")
         (roles/show-reviews? roles) (dispatch! "/#/actions")
         :else (dispatch! "/#/catalogue"))
       {})
     ;;; else dispatch the same event again while waiting for set-identity (happens especially with Firefox)
     {:dispatch [:landing-page-redirect!]})))

(defn home-page []
  (if @(rf/subscribe [:user])
    ;; TODO this is a hack to show something useful on the home page
    ;; when we are logged in. We can't really perform a dispatch!
    ;; here, because that would be a race condition with #fragment
    ;; handling in hook-history-navigation!
    ;;
    ;; One possibility is to have a separate :init default page that
    ;; does the navigation/redirect logic, instead of using :home as
    ;; the default.
    (do
      (rf/dispatch [:rems.catalogue/enter-page])
      [catalogue-page])
    [auth/login-component]))

(defn unauthorized-page []
  [:div
   [:h2 (text :t.unauthorized-page/unauthorized)]
   [:p (text :t.unauthorized-page/you-are-unauthorized)]])

(defn forbidden-page []
  [:div
   [:h2 (text :t.forbidden-page/forbidden)]
   [:p (text :t.forbidden-page/you-are-forbidden)]])

(defn not-found-page []
  [:div
   [:h2 (text :t.not-found-page/not-found)]
   [:p (text :t.not-found-page/page-was-not-found)]])

(def pages
  {:home home-page
   :catalogue catalogue-page
   :guide guide-page
   :actions actions-page
   :application application-page
   :new-application new-application-page
   :applications applications-page
   :extra-pages extra-pages
   :rems.actions/accept-invitation accept-invitation-page
   :rems.administration/administration administration-page
   :rems.administration/catalogue-item catalogue-item-page
   :rems.administration/catalogue-items catalogue-items-page
   :rems.administration/create-catalogue-item create-catalogue-item-page
   :rems.administration/create-form create-form-page
   :rems.administration/create-license create-license-page
   :rems.administration/create-resource create-resource-page
   :rems.administration/create-workflow create-workflow-page
   :rems.administration/form form-page
   :rems.administration/forms forms-page
   :rems.administration/license license-page
   :rems.administration/licenses licenses-page
   :rems.administration/resource resource-page
   :rems.administration/resources resources-page
   :rems.administration/workflow workflow-page
   :rems.administration/workflows workflows-page
   :unauthorized unauthorized-page
   :forbidden forbidden-page
   :not-found not-found-page})

(defn footer []
  [:footer.footer
   [:div.container [:nav.navbar
                    [:div.navbar-text (text :t/footer)]
                    (when-let [{:keys [version revision repo-url]} (read-current-version)]
                      [:div#footer-release-number
                       [:a {:href (str repo-url revision)}
                        version]])]]])

(defn logo []
  [:div.logo [:div.container.img]])

(defn page []
  (let [page-id @(rf/subscribe [:page])
        content (pages page-id)]
    [:div
     [nav/navigation-widget page-id]
     [status-modal/status-modal]
     [logo]
     [:div.container-fluid.main-content
      {:class (str "page-" (name page-id))}
      [content]]
     [footer]]))

(reg-event-fx
 :after-translations-are-loaded
 (fn [{:keys [db]} [_ on-loaded]]
   (if (seq (:translations db))
     (on-loaded)
     (.setTimeout js/window #(dispatch [:after-translations-are-loaded on-loaded]) 100))
   {}))

;; -------------------------
;; Routes

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

(secretary/defroute "/catalogue" []
  (rf/dispatch [:rems.catalogue/enter-page])
  (rf/dispatch [:set-active-page :catalogue]))

(secretary/defroute "/guide" []
  (rf/dispatch [:set-active-page :guide]))

(secretary/defroute "/actions" []
  (rf/dispatch [:rems.actions/enter-page])
  (rf/dispatch [:set-active-page :actions]))

(secretary/defroute "/application/accept-invitation/:invitation-token" [invitation-token]
  (rf/dispatch [:after-translations-are-loaded
                #(do
                   (rf/dispatch [:rems.actions.accept-invitation/enter-page invitation-token])
                   (rf/dispatch [:set-active-page :rems.actions/accept-invitation]))]))

(secretary/defroute "/application/:id" {id :id}
  (rf/dispatch [:rems.application/enter-application-page id])
  (rf/dispatch [:set-active-page :application]))

(secretary/defroute "/application" {{items :items} :query-params}
  (rf/dispatch [:rems.new-application/enter-new-application-page (cart/parse-items items)])
  (rf/dispatch [:set-active-page :new-application]))

(secretary/defroute "/applications" []
  (rf/dispatch [:rems.applications/enter-page])
  (rf/dispatch [:set-active-page :applications]))

(secretary/defroute "/administration" []
  (rf/dispatch [:rems.administration.administration/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/administration]))

(secretary/defroute "/administration/catalogue-items/:catalogue-item-id" [catalogue-item-id]
  (rf/dispatch [:rems.administration.catalogue-item/enter-page catalogue-item-id])
  (rf/dispatch [:set-active-page :rems.administration/catalogue-item]))

(secretary/defroute "/administration/catalogue-items" []
  (rf/dispatch [:rems.administration.catalogue-items/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/catalogue-items]))

(secretary/defroute "/administration/forms/:form-id" [form-id]
  (rf/dispatch [:rems.administration.form/enter-page form-id])
  (rf/dispatch [:set-active-page :rems.administration/form]))

(secretary/defroute "/administration/forms" []
  (rf/dispatch [:rems.administration.forms/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/forms]))

(secretary/defroute "/administration/resources/:resource-id" [resource-id]
  (rf/dispatch [:rems.administration.resource/enter-page resource-id])
  (rf/dispatch [:set-active-page :rems.administration/resource]))

(secretary/defroute "/administration/resources" []
  (rf/dispatch [:rems.administration.resources/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/resources]))

(secretary/defroute "/administration/workflows/:workflow-id" [workflow-id]
  (rf/dispatch [:rems.administration.workflow/enter-page workflow-id])
  (rf/dispatch [:set-active-page :rems.administration/workflow]))

(secretary/defroute "/administration/workflows" []
  (rf/dispatch [:rems.administration.workflows/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/workflows]))

(secretary/defroute "/administration/licenses/:license-id" [license-id]
  (rf/dispatch [:rems.administration.license/enter-page license-id])
  (rf/dispatch [:set-active-page :rems.administration/license]))

(secretary/defroute "/administration/licenses" []
  (rf/dispatch [:rems.administration.licenses/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/licenses]))

(secretary/defroute "/administration/create-catalogue-item" []
  (rf/dispatch [:rems.administration.create-catalogue-item/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-catalogue-item]))

(secretary/defroute "/administration/create-form" []
  (rf/dispatch [:rems.administration.create-form/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/create-license" []
  (rf/dispatch [:rems.administration.create-license/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-license]))

(secretary/defroute "/administration/create-resource" []
  (rf/dispatch [:rems.administration.create-resource/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-resource]))

(secretary/defroute "/administration/create-workflow" []
  (rf/dispatch [:rems.administration.create-workflow/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-workflow]))

(secretary/defroute "/administration/edit-workflow/:workflow-id" [workflow-id]
  (rf/dispatch [:rems.administration.create-workflow/enter-page (parse-int workflow-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-workflow]))

(secretary/defroute "/extra-pages/:page-id" [page-id]
  (rf/dispatch [:rems.extra-pages/enter-page page-id])
  (rf/dispatch [:set-active-page :extra-pages]))

(secretary/defroute "/unauthorized" []
  (rf/dispatch [:set-active-page :unauthorized]))

(secretary/defroute "/forbidden" []
  (rf/dispatch [:set-active-page :forbidden]))

(secretary/defroute "/redirect" []
  ;; user is logged in so redirect to a more specific page
  (if-let [url (.getItem js/sessionStorage "rems-redirect-url")]
    (do
      (println "Redirecting to" url "after authorization")
      (.removeItem js/sessionStorage "rems-redirect-url")
      (dispatch! url))
    (rf/dispatch [:landing-page-redirect!])))

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
       (js/window.rems.hooks.navigate (.-token event))
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn fetch-translations! []
  (fetch "/api/translations" {:handler #(rf/dispatch [:loaded-translations %])}))

(defn fetch-theme! []
  (fetch "/api/theme" {:handler #(rf/dispatch [:loaded-theme %])}))

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
