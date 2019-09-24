(ns rems.spa
  (:require [accountant.core :as accountant]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [re-frame.core :as rf]
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
            [rems.atoms :refer [document-title]]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :refer [catalogue-page]]
            [rems.config :as config]
            [rems.extra-pages :refer [extra-pages]]
            [rems.flash-message :as flash-message]
            [rems.guide-page :refer [guide-page]]
            [rems.navbar :as nav]
            [rems.new-application :refer [new-application-page]]
            [rems.roles :as roles]
            [rems.text :refer [text]]
            [rems.user-settings :refer [fetch-user-settings!]]
            [rems.util :refer [navigate! fetch parse-int]]
            [secretary.core :as secretary])
  (:require-macros [rems.read-gitlog :refer [read-current-version]])
  (:import goog.history.Html5History))

;;; subscriptions

(rf/reg-sub
 :page
 (fn [db _]
   (:page db)))

(rf/reg-sub
 :docs
 (fn [db _]
   (:docs db)))

;; TODO: possibly move translations out
(rf/reg-sub
 :translations
 (fn [db _]
   (:translations db)))

;; TODO: possibly move theme out
(rf/reg-sub
 :theme
 (fn [db _]
   (:theme db)))

;;; handlers

(rf/reg-event-db
 :initialize-db
 (fn [_ _]
   {:page :home
    :languages [:en]
    :default-language :en
    :translations {}
    :identity {:user nil :roles nil}}))

(rf/reg-event-db
 :set-active-page
 (fn [db [_ page]]
   (assoc db :page page)))

(rf/reg-event-db
 :set-docs
 (fn [db [_ docs]]
   (assoc db :docs docs)))

(rf/reg-event-db
 :loaded-translations
 (fn [db [_ translations]]
   (assoc db :translations translations)))

(rf/reg-event-db
 :loaded-theme
 (fn [db [_ theme]]
   (assoc db :theme theme)))

(rf/reg-event-fx
 :unauthorized!
 (fn [_ [_ current-url]]
   (println "Received unauthorized from" current-url)
   (.setItem js/sessionStorage "rems-redirect-url" current-url)
   (navigate! "/")
   {}))

(rf/reg-event-fx
 :forbidden!
 (fn [_ [_ current-url]]
   (println "Received forbidden from" current-url)
   {:dispatch [:set-active-page :forbidden]}))

(rf/reg-event-fx
 :landing-page-redirect!
 (fn [{:keys [db]}]
   ;; do we have the roles set by set-identity already?
   (if (get-in db [:identity :roles])
     (let [roles (get-in db [:identity :roles])]
       (println "Selecting landing page based on roles" roles)
       (.removeItem js/sessionStorage "rems-redirect-url")
       (cond
         (roles/show-admin-pages? roles) (navigate! "/administration")
         (roles/show-reviews? roles) (navigate! "/actions")
         :else (navigate! "/catalogue"))
       {})
     ;;; else dispatch the same event again while waiting for set-identity (happens especially with Firefox)
     {:dispatch [:landing-page-redirect!]})))

(rf/reg-event-db
 ::user-triggered-navigation
 (fn [db [_]]
   (.scrollTo js/window 0 0)
   (assoc db ::grab-focus? true)))

(rf/reg-event-db
 ::focus-grabbed
 (fn [db [_]]
   (assoc db ::grab-focus? false)))

(rf/reg-sub
 ::grab-focus?
 (fn [db _]
   (::grab-focus? db)))

(defn home-page []
  (if @(rf/subscribe [:user])
    (do
      (fetch-user-settings!)
      ;; TODO: separate :init default page that does the navigation/redirect logic, instead of using :home as the default
      (when (= "/" js/window.location.pathname)
        (navigate! "/catalogue"))
      nil)
    [:div
     [:div.row.justify-content-center
      [:div.col-md-6.row.justify-content-center
       (text :t.login/intro)]]
     [:div.row.justify-content-center
      [:div.col-md-6.row.justify-content-center
       [auth/login-component]]]]))

(defn unauthorized-page []
  [:div
   [document-title (text :t.unauthorized-page/unauthorized)]
   [flash-message/component :top]
   [:p (text :t.unauthorized-page/you-are-unauthorized)]])

(defn forbidden-page []
  [:div
   [document-title (text :t.forbidden-page/forbidden)]
   [flash-message/component :top]
   [:p (text :t.forbidden-page/you-are-forbidden)]])

(defn not-found-page []
  [:div
   [document-title (text :t.not-found-page/not-found)]
   [flash-message/component :top]
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
   [:div.container
    [:div.navbar
     [:div.navbar-text (text :t/footer)]
     (when-let [{:keys [version revision repo-url]} (read-current-version)]
       [:div#footer-release-number
        [:a {:href (str repo-url revision)}
         version]])]]])

(defn logo []
  [:div.logo [:div.container.img]])

(defn main-content [_page-id _grab-focus?]
  (let [on-update (fn [this]
                    (let [[_ _page-id grab-focus?] (r/argv this)]
                      (when grab-focus?
                        (when-let [element (.querySelector js/document "#main-content")]
                          (.setAttribute element "tabindex" "-1")
                          (.focus element)
                          (rf/dispatch [::focus-grabbed])))))]
    (r/create-class
     {:component-did-mount on-update
      :component-did-update on-update
      :display-name "main-content"
      :reagent-render (fn [page-id _grab-focus?]
                        (let [content (pages page-id)]
                          [:main.container-fluid
                           {:class (str "page-" (name page-id))
                            :id "main-content"}
                           [content]]))})))

(defn page []
  (let [page-id @(rf/subscribe [:page])
        grab-focus? @(rf/subscribe [::grab-focus?])]
    [:div
     [nav/navigation-widget page-id]
     [logo]
     [main-content page-id grab-focus?]
     [footer]]))

(rf/reg-event-fx
 :after-translations-are-loaded
 (fn [{:keys [db]} [_ on-loaded]]
   (if (seq (:translations db))
     (on-loaded)
     (.setTimeout js/window #(rf/dispatch [:after-translations-are-loaded on-loaded]) 100))
   {}))

;; -------------------------
;; Routes

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

(secretary/defroute "/administration/edit-catalogue-item/:catalogue-item-id" [catalogue-item-id]
  (rf/dispatch [:rems.administration.create-catalogue-item/enter-page (parse-int catalogue-item-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-catalogue-item]))

(secretary/defroute "/administration/create-form" []
  (rf/dispatch [:rems.administration.create-form/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/create-form/:form-id" [form-id]
  (rf/dispatch [:rems.administration.create-form/enter-page (parse-int form-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/edit-form/:form-id" [form-id]
  (rf/dispatch [:rems.administration.create-form/enter-page (parse-int form-id) true])
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
      (navigate! url))
    (rf/dispatch [:landing-page-redirect!])))

(secretary/defroute "*" []
  (rf/dispatch [:set-active-page :not-found]))

;; -------------------------
;; History
;; must be called after routes have been defined

(defn hook-browser-navigation! []
  ;; This listener is called when the page is first loaded
  ;; an on every subsequent navigation.
  (events/listen accountant/history
                 HistoryEventType/NAVIGATE
                 (fn [event]
                   (js/window.rems.hooks.navigate (.-token event))))

  (accountant/configure-navigation!
   {:nav-handler (let [previous-path (atom nil)]
                   (fn [path]
                     ;; XXX: workaround for Secretary/Accountant considering URLs with different hash to be different pages
                     (let [url (js/URL. path js/location)
                           path-without-hash (str (.-pathname url) (.-search url))]
                       (when-not (= @previous-path path-without-hash)
                         (reset! previous-path path-without-hash)
                         (secretary/dispatch! path-without-hash)))))
    :path-exists? (fn [path]
                    (let [route (secretary/locate-route path)
                          not-found-page? (= "*" (secretary/route-value (:route route)))]
                      (when-not not-found-page?
                        route)))})
  (accountant/dispatch-current!)

  ;; This listener is NOT called on the initial page load,
  ;; but only on subsequent navigations. On the initial
  ;; full page load the focus does not need to be changed.
  (events/listen accountant/history
                 HistoryEventType/NAVIGATE
                 (fn [_event]
                   (rf/dispatch [:rems.spa/user-triggered-navigation]))))


;; -------------------------
;; Initialize app

(defn fetch-translations! []
  (fetch "/api/translations"
         {:handler #(rf/dispatch [:loaded-translations %])
          :error-handler (flash-message/default-error-handler :top "Fetch translations")}))

(defn fetch-theme! []
  (fetch "/api/theme"
         {:handler #(rf/dispatch [:loaded-theme %])
          :error-handler (flash-message/default-error-handler :top "Fetch theme")}))

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
