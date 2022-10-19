(ns rems.spa
  (:require [accountant.core :as accountant]
            [clojure.string :as str]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [rems.actions :refer [actions-page]]
            [rems.actions.accept-invitation :refer [accept-invitation-page]]
            [rems.accept-invitation :as accept-invitation]
            [rems.administration.blacklist :refer [blacklist-page]]
            [rems.administration.catalogue-item :refer [catalogue-item-page]]
            [rems.administration.catalogue-items :refer [catalogue-items-page]]
            [rems.administration.categories :refer [categories-page]]
            [rems.administration.category :refer [category-page]]
            [rems.administration.change-catalogue-item-form :refer [change-catalogue-item-form-page]]
            [rems.administration.create-catalogue-item :refer [create-catalogue-item-page]]
            [rems.administration.create-category :refer [create-category-page]]
            [rems.administration.create-form :refer [create-form-page]]
            [rems.administration.create-license :refer [create-license-page]]
            [rems.administration.create-organization :refer [create-organization-page]]
            [rems.administration.create-resource :refer [create-resource-page]]
            [rems.administration.create-workflow :refer [create-workflow-page]]
            [rems.administration.edit-category :refer [edit-category-page]]
            [rems.administration.export-applications :refer [export-applications-page]]
            [rems.administration.form :refer [form-page]]
            [rems.administration.forms :refer [forms-page]]
            [rems.administration.license :refer [license-page]]
            [rems.administration.licenses :refer [licenses-page]]
            [rems.administration.organization :refer [organization-page]]
            [rems.administration.organizations :refer [organizations-page]]
            [rems.administration.reports :refer [reports-page]]
            [rems.administration.resource :refer [resource-page]]
            [rems.administration.resources :refer [resources-page]]
            [rems.administration.workflow :refer [workflow-page]]
            [rems.administration.workflows :refer [workflows-page]]
            [rems.ajax :refer [load-interceptors!]]
            [rems.application :refer [application-page]]
            [rems.applications :refer [applications-page]]
            [rems.atoms :refer [document-title logo]]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :refer [catalogue-page]]
            [rems.common.util :refer [parse-int]]
            [rems.config :as config]
            [rems.extra-pages :refer [extra-pages]]
            [rems.flash-message :as flash-message]
            [rems.focus :as focus]
            [rems.common.git :as git]
            [rems.guide-page :refer [guide-page]]
            [rems.hooks] ; for the empty hooks
            [rems.keepalive :as keepalive]
            [rems.navbar :as nav]
            [rems.new-application :refer [new-application-page]]
            [rems.common.roles :as roles]
            [rems.profile :refer [profile-page missing-email-warning]]
            [rems.text :refer [text text-format]]
            [rems.user-settings]
            [rems.util :refer [navigate! fetch replace-url! set-location!]]
            [secretary.core :as secretary])
  (:import goog.history.Html5History))

(defn- fetch-translations! []
  (fetch "/api/translations"
         {:handler #(rf/dispatch-sync [:loaded-translations %])
          :error-handler (flash-message/default-error-handler :top "Fetch translations")}))

(defn- fetch-theme! []
  (fetch "/api/theme"
         {:handler #(rf/dispatch-sync [:loaded-theme %])
          :error-handler (flash-message/default-error-handler :top "Fetch theme")}))

;;;; Global events & subscriptions

(rf/reg-sub
 :page
 (fn [db _]
   (:page db)))

(rf/reg-sub
 :path
 (fn [db _]
   (or (:path db) "")))

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

(rf/reg-sub
 :error
 (fn [db _]
   (:error db)))

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
 :set-error
 (fn [db [_ error]]
   (assoc db :error error)))

(rf/reg-event-db
 :set-path
 (fn [db [_ path]]
   (assoc db :path path)))

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
   (set-location! "/") ;; we want to refresh identity
   {}))

(rf/reg-event-fx
 :forbidden!
 (fn [_ [_ current-url]]
   (println "Received forbidden from" current-url)
   {:dispatch [:set-active-page :forbidden]}))

(rf/reg-event-fx
 :not-found!
 (fn [_ [_ current-url]]
   (println "Received not-found from" current-url)
   {:dispatch [:set-active-page :not-found]}))

(defn version-info []
  (if-let [{:keys [version revision]} git/+version+]
    (do (println "Version:" version)
        (println (str git/+commits-url+ revision)))
    (println "Version information not available")))

(rf/reg-event-fx
 :landing-page-redirect!
 (fn [{:keys [db]}]
   ;; do we have the roles set by set-identity already?
   (if (get-in db [:identity :roles])
     (let [roles (get-in db [:identity :roles])]
       (println "Selecting landing page based on roles" roles)
       (.removeItem js/sessionStorage "rems-redirect-url")
       (cond
         (roles/show-reviews? roles) (navigate! "/actions")
         (roles/show-admin-pages? roles) (navigate! "/administration")
         :else (navigate! "/catalogue"))
       {})
     ;; else dispatch the same event again while waiting for set-identity (happens especially with Firefox)
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

(rf/reg-event-fx
 :after-translations-are-loaded
 (fn [{:keys [db]} [_ on-loaded]]
   (if (seq (:translations db))
     (on-loaded)
     (.setTimeout js/window #(rf/dispatch [:after-translations-are-loaded on-loaded]) 100))
   {}))

;;;; Pages
(defn login-intro []
  [:div
   [:div.row.justify-content-center
    [flash-message/component :top]]
   [:div.row.justify-content-center
    [:div.col-md-6.row
     (text :t.login/intro)]]
   [:div.row.justify-content-center
    [:div.col-md-6.row
     [auth/login-component]]]
   [:div.row.justify-content-center
    [:div.col-md-6.row
     (text :t.login/intro2)]]])

(defn home-page []
  (if @(rf/subscribe [:user])
    (do
      ;; TODO: have a separate :init default page that does the navigation/redirect logic, instead of using :home as the default
      (when (= "/" js/window.location.pathname)
        (navigate! "/catalogue"))
      nil)
    (do
      (when-let [redirect-url (-> (js/URLSearchParams. js/window.location.search)
                                  (.get "redirect"))]
        (.setItem js/sessionStorage "rems-redirect-url" redirect-url))
      [login-intro])))

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

(defn error-page
  "Generic error page"
  []
  (let [error @(rf/subscribe [:error])]
    [:div
     [document-title (text :t.error-page/title)]
     [flash-message/component :top]
     (let [args (:args error)]
       [:p (text-format
            (:key error)
            (if (string? args)
              (text args)
              (str/join ", " (map text args))))])]))

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
   :profile profile-page
   :rems.accept-invitation/accept-invitation-page accept-invitation/accept-invitation-page
   :rems.actions/accept-invitation accept-invitation-page
   :rems.administration/blacklist blacklist-page
   :rems.administration/catalogue-item catalogue-item-page
   :rems.administration/catalogue-items catalogue-items-page
   :rems.administration/categories categories-page
   :rems.administration/category category-page
   :rems.administration/change-catalogue-item-form change-catalogue-item-form-page
   :rems.administration/create-catalogue-item create-catalogue-item-page
   :rems.administration/create-category create-category-page
   :rems.administration/create-form create-form-page
   :rems.administration/create-license create-license-page
   :rems.administration/create-organization create-organization-page
   :rems.administration/create-resource create-resource-page
   :rems.administration/create-workflow create-workflow-page
   :rems.administration/edit-category edit-category-page
   :rems.administration/export-applications export-applications-page
   :rems.administration/form form-page
   :rems.administration/forms forms-page
   :rems.administration/license license-page
   :rems.administration/licenses licenses-page
   :rems.administration/organization organization-page
   :rems.administration/organizations organizations-page
   :rems.administration/reports reports-page
   :rems.administration/resource resource-page
   :rems.administration/resources resources-page
   :rems.administration/workflow workflow-page
   :rems.administration/workflows workflows-page
   :unauthorized unauthorized-page
   :forbidden forbidden-page
   :error error-page
   :not-found not-found-page})

(defn- dev-reload-button
  "Loads the initial translations, theme and config again.

  Useful while developing and changing e.g. translations."
  []
  [:div.dev-reload-button
   [:button.btn.btn-secondary.btn-sm
    {:on-click #(do (fetch-translations!)
                    (fetch-theme!)
                    (config/fetch-config!))}
    [:i.fas.fa-redo]]])

(defn footer []
  [:footer.footer
   [:div.container.d-flex.flex-row.justify-content-between.align-items-center
    [:div.d-flex.flex-row [nav/footer-extra-pages]]
    [:div.footer-text (text :t/footer)]
    (when (config/dev-environment?)
      [dev-reload-button])]])

(defn main-content [_page-id _grab-focus?]
  (let [on-update (fn [this]
                    (let [[_ _page-id grab-focus?] (r/argv this)]
                      (when grab-focus?
                        (when-let [element (or (.querySelector js/document "h1")
                                               (.querySelector js/document "#main-content"))]
                          (focus/focus element)
                          (rf/dispatch [::focus-grabbed])))))]
    (r/create-class
     {:component-did-mount on-update
      :component-did-update on-update
      :display-name "main-content"
      :reagent-render (fn [page-id _grab-focus?]
                        [:main.container-fluid
                         {:class (str "page-" (name page-id))
                          :id "main-content"}
                         (if-let [content (pages page-id)]
                           [:<>
                            [missing-email-warning]
                            [content]]
                           (do ; implementation error
                             (println "Unknown page-id" page-id)
                             (rf/dispatch [:set-active-page :not-found])
                             nil))])})))

(defn- lazy-load-data!
  "Loads datasets that are not required for immediate render or e.g. require login."
  []
  (when @(rf/subscribe [:user])
    (when (empty? @(rf/subscribe [:organizations]))
      (config/fetch-organizations!))))

(defn page []
  (let [page-id @(rf/subscribe [:page])
        grab-focus? @(rf/subscribe [::grab-focus?])
        theme @(rf/subscribe [:theme])
        lang @(rf/subscribe [:language])]
    (lazy-load-data!)
    [:div
     [nav/navigation-widget]
     (when (or (= page-id :home)
               (and lang
                    (not ((keyword (str "navbar-logo-name-" (name lang))) theme))
                    (not (:navbar-logo-name theme))))
       [logo])
     (when page-id
       [main-content page-id grab-focus?])
     [footer]]))

;;;; Routes

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

(secretary/defroute "/invitation/accept-invitation" {{token :token type :type} :query-params}
  (rf/dispatch [:after-translations-are-loaded
                #(do
                   (rf/dispatch [:rems.accept-invitation/enter-page (keyword type) token])
                   (rf/dispatch [:set-active-page :rems.accept-invitation/accept-invitation-page]))]))

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
  (replace-url! "/administration/catalogue-items"))

(secretary/defroute "/administration/organizations/create" []
  (rf/dispatch [:rems.administration.create-organization/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-organization]))

(secretary/defroute "/administration/organizations/edit/:organization-id" [organization-id]
  (rf/dispatch [:rems.administration.create-organization/enter-page organization-id])
  (rf/dispatch [:set-active-page :rems.administration/create-organization]))

(secretary/defroute "/administration/organizations/:organization-id" [organization-id]
  (rf/dispatch [:rems.administration.organization/enter-page organization-id])
  (rf/dispatch [:set-active-page :rems.administration/organization]))

(secretary/defroute "/administration/organizations" []
  (rf/dispatch [:rems.administration.organizations/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/organizations]))

(secretary/defroute "/administration/reports" []
  (rf/dispatch [:rems.administration.reports/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/reports]))

(secretary/defroute "/administration/reports/export-applications" []
  (rf/dispatch [:rems.administration.export-applications/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/export-applications]))

(secretary/defroute "/administration/blacklist" []
  (rf/dispatch [:rems.administration.blacklist/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/blacklist]))

(secretary/defroute "/administration/catalogue-items/change-form" []
  (rf/dispatch [:set-active-page :rems.administration/change-catalogue-item-form]))

(secretary/defroute "/administration/catalogue-items/create" []
  (rf/dispatch [:rems.administration.create-catalogue-item/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-catalogue-item]))

(secretary/defroute "/administration/catalogue-items/edit/:catalogue-item-id" [catalogue-item-id]
  (rf/dispatch [:rems.administration.create-catalogue-item/enter-page (parse-int catalogue-item-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-catalogue-item]))

(secretary/defroute "/administration/catalogue-items/:catalogue-item-id" [catalogue-item-id]
  (rf/dispatch [:rems.administration.catalogue-item/enter-page catalogue-item-id])
  (rf/dispatch [:set-active-page :rems.administration/catalogue-item]))

(secretary/defroute "/administration/catalogue-items" []
  (rf/dispatch [:rems.administration.catalogue-items/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/catalogue-items]))

(secretary/defroute "/administration/categories" []
  (rf/dispatch [:rems.administration.categories/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/categories]))

(secretary/defroute "/administration/categories/create" []
  (rf/dispatch [:rems.administration.create-category/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-category]))

(secretary/defroute "/administration/categories/edit/:category-id" [category-id]
  (rf/dispatch [:rems.administration.edit-category/enter-page category-id])
  (rf/dispatch [:set-active-page :rems.administration/edit-category]))

(secretary/defroute "/administration/categories/:category-id" [category-id]
  (rf/dispatch [:rems.administration.category/enter-page category-id])
  (rf/dispatch [:set-active-page :rems.administration/category]))

(secretary/defroute "/administration/forms/create" []
  (rf/dispatch [:rems.administration.create-form/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/forms/create/:form-id" [form-id]
  (rf/dispatch [:rems.administration.create-form/enter-page (parse-int form-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/forms/edit/:form-id" [form-id]
  (rf/dispatch [:rems.administration.create-form/enter-page (parse-int form-id) true])
  (rf/dispatch [:set-active-page :rems.administration/create-form]))

(secretary/defroute "/administration/forms/:form-id" [form-id]
  (rf/dispatch [:rems.administration.form/enter-page form-id])
  (rf/dispatch [:set-active-page :rems.administration/form]))

(secretary/defroute "/administration/forms" []
  (rf/dispatch [:rems.administration.forms/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/forms]))

(secretary/defroute "/administration/resources/create" []
  (rf/dispatch [:rems.administration.create-resource/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-resource]))

(secretary/defroute "/administration/resources/:resource-id" [resource-id]
  (rf/dispatch [:rems.administration.resource/enter-page resource-id])
  (rf/dispatch [:set-active-page :rems.administration/resource]))

(secretary/defroute "/administration/resources" []
  (rf/dispatch [:rems.administration.resources/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/resources]))

(secretary/defroute "/administration/workflows/create" []
  (rf/dispatch [:rems.administration.create-workflow/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-workflow]))

(secretary/defroute "/administration/workflows/edit/:workflow-id" [workflow-id]
  (rf/dispatch [:rems.administration.create-workflow/enter-page (parse-int workflow-id)])
  (rf/dispatch [:set-active-page :rems.administration/create-workflow]))

(secretary/defroute "/administration/workflows/:workflow-id" [workflow-id]
  (rf/dispatch [:rems.administration.workflow/enter-page workflow-id])
  (rf/dispatch [:set-active-page :rems.administration/workflow]))

(secretary/defroute "/administration/workflows" []
  (rf/dispatch [:rems.administration.workflows/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/workflows]))

(secretary/defroute "/administration/licenses/create" []
  (rf/dispatch [:rems.administration.create-license/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/create-license]))

(secretary/defroute "/administration/licenses/:license-id" [license-id]
  (rf/dispatch [:rems.administration.license/enter-page license-id])
  (rf/dispatch [:set-active-page :rems.administration/license]))

(secretary/defroute "/administration/licenses" []
  (rf/dispatch [:rems.administration.licenses/enter-page])
  (rf/dispatch [:set-active-page :rems.administration/licenses]))

(secretary/defroute "/extra-pages/:page-id" [page-id]
  (rf/dispatch [:rems.extra-pages/enter-page page-id])
  (rf/dispatch [:set-active-page :extra-pages]))

(secretary/defroute "/profile" []
  (rf/dispatch [:rems.profile/enter-page])
  (rf/dispatch [:set-active-page :profile]))

(secretary/defroute "/unauthorized" []
  (rf/dispatch [:set-active-page :unauthorized]))

(secretary/defroute "/forbidden" []
  (rf/dispatch [:set-active-page :forbidden]))

;; XXX: could use schema?
(defn- fix-error-from-query-params
  "Fixes the error value we receive from query parameters by transforming the strings into keywords etc."
  [error]
  (cond (map? error)
        (into {} (for [[k v] error]
                   [(keyword k) (fix-error-from-query-params v)]))

        (coll? error)
        (mapv fix-error-from-query-params error)

        (not (string? error))
        error

        (str/starts-with? error ":t")
        (apply keyword (str/split (subs error 1) "/"))

        :else error))

(secretary/defroute "/error" {params :query-params}
  (rf/dispatch [:set-error (fix-error-from-query-params params)])
  (rf/dispatch [:set-active-page :error]))

(secretary/defroute "/redirect" []
  ;; user is logged in so redirect to a more specific page
  (if-let [url (.getItem js/sessionStorage "rems-redirect-url")]
    (do
      (println "Redirecting to" url "after authorization")
      (.removeItem js/sessionStorage "rems-redirect-url")
      ;; not using `navigate!` because e.g. attachment download link requires a full page load
      (set-location! url))
    (rf/dispatch [:landing-page-redirect!])))

;;;; History
;; must be called after routes have been defined

(defn hook-browser-navigation! []
  (events/listen accountant/history
                 HistoryEventType/NAVIGATE
                 (fn [^js event]
                   (js/window.rems.hooks.navigate (.-token event))))

  (accountant/configure-navigation!
   {:nav-handler (let [previous-path (atom nil)]
                   (fn [path]
                     ;; XXX: workaround for Secretary/Accountant considering URLs with different hash to be different pages
                     (let [url (js/URL. path js/location)
                           path-without-hash (str (.-pathname url) (.-search url))]
                       (rf/dispatch [:set-path path-without-hash])
                       (when-not (= @previous-path path-without-hash)
                         (reset! previous-path path-without-hash)
                         (secretary/dispatch! path-without-hash)))))
    :path-exists? (fn [path]
                    (let [route (secretary/locate-route path)
                          not-found-page? (= "*" (secretary/route-value (:route route)))]
                      (when-not not-found-page?
                        route)))})
  (accountant/dispatch-current!)

  ;; Since this listener is registered after configuring Accountant,
  ;; it will NOT be called on the initial page load; we don't need nor
  ;; want to change focus on the initial full page load.
  (events/listen accountant/history
                 HistoryEventType/NAVIGATE
                 (fn [_event]
                   (rf/dispatch [:rems.spa/user-triggered-navigation]))))

;;;; Initialize app

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rd/render [page] (.getElementById js/document "app")))

(defn ^:export init []
  (version-info)
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (keepalive/register-keepalive-listeners!)
  ;; see also: lazy-load-data! and dev-reload-button
  (hook-browser-navigation!))

(defn ^:export mount []
  (mount-components))
