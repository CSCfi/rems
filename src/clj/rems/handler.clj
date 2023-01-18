(ns rems.handler
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [rems.api :refer [api-routes]]
            [rems.service.attachment :as attachment]
            [rems.service.invitation :as invitation]
            [rems.service.licenses :as licenses]
            [rems.api.util :as api-util]
            [rems.context :as context]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.css.styles :as styles]
            [rems.db.catalogue :as catalogue]
            [rems.email.core] ;; to enable email polling
            [rems.application.eraser] ;; to enable expired application clean-up job
            [rems.layout :as layout]
            [rems.middleware :refer [wrap-cacheable wrap-base]]
            [rems.util :refer [getx-user-id never-match-route]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [content-type file-response not-found bad-request redirect]])
  (:import [rems.auth UnauthorizedException]))

(defn- resource-to-item [resource]
  (let [items (->> (catalogue/get-localized-catalogue-items {:resource resource})
                   (filter :enabled))]
    (cond
      (= 0 (count items)) :not-found
      (< 1 (count items)) :not-unique
      :else (first items))))

(defn- apply-for-resources [resources]
  (let [items (map resource-to-item resources)]
    (cond
      (some #{:not-found} items) (-> (not-found "Resource not found")
                                     (content-type "text/plain"))
      (some #{:not-unique} items) (-> (bad-request "Catalogue item is not unique")
                                      (content-type "text/plain"))
      (not (apply = (mapv :wfid items))) (-> (bad-request "Unbundlable catalogue items: workflows don't match")
                                             (content-type "text/plain"))
      :else (redirect (str "/application?items=" (str/join "," (mapv :id items)))))))

(defroutes redirects
  (GET "/accept-invitation" [token]
    (if-let [invitation (first (invitation/get-invitations {:token token}))]
      (cond (:invitation/workflow invitation)
            (redirect (str "/invitation/accept-invitation?type=workflow&token=" token)))
      (redirect (str "/application/accept-invitation/" token))))

  (GET "/apply-for" [resource] ;; can specify multiple resources
    (if (vector? resource)
      (apply-for-resources resource)
      (apply-for-resources [resource])))

  (GET "/landing_page" [] ; DEPRECATED: legacy url redirect
    (redirect "/redirect"))

  (GET "/favicon.ico" []
    (redirect "/img/favicon.ico"))

  (GET "/entitlements.csv" [] ; DEPRECATED: legacy url redirect 
    (redirect "/api/entitlements/export-csv")))

(defroutes attachment-routes
  (GET "/applications/attachment/:attachment-id" [attachment-id]
    (let [attachment-id (Long/parseLong attachment-id)]
      (api-util/check-user)
      (if-let [attachment (attachment/get-application-attachment (getx-user-id) attachment-id)]
        (attachment/download attachment)
        (api-util/not-found-text-response))))

  (GET "/applications/:application-id/license-attachment/:license-id/:language" [application-id license-id language]
    (let [application-id (Long/parseLong application-id)
          license-id (Long/parseLong license-id)
          language (keyword language)]
      (api-util/check-user)
      (if-let [attachment (licenses/get-application-license-attachment (getx-user-id) application-id license-id language)]
        (attachment/download attachment)
        (api-util/not-found-text-response)))))

(defn wrap-login-redirect [handler]
  (fn [req]
    (try
      (handler req)
      (catch UnauthorizedException _
        (redirect (str "/?redirect=" (url-encode (:uri req))))))))

(defn not-found-handler [_req]
  ;; TODO: serve 404 for routes which the frontend doesn't recognize
  #_(layout/error-page {:status 404
                        :title "Page not found"})
  (layout/home-page))

(def home-route
  (GET "/" [] (layout/home-page)))

(defn extra-script-routes [{:keys [root files]}]
  (let [files (set files)]
    (fn [request]
      (when (contains? files (:uri request))
        (file-response (:uri request) {:root root})))))

(defn extra-stylesheet-routes [{:keys [root files]}]
  (let [files (set files)]
    (fn [request]
      (when (contains? files (:uri request))
        (file-response (:uri request) {:root root})))))

(defn- static-resources [path]
  (if path
    (route/files "/" {:root path})
    never-match-route))

(def ^:private webjar-handler
  "Serves our webjar (https://www.webjars.org/) dependencies as /assets/<webjar>/<file>.
   Weirdly ring-webjars only exposes a middleware and not a route."
  (wrap-webjars never-match-route))

(def ^:private resource-handler
  (route/resources "/" {:root "public"}))

(defn app-routes []
  (routes
   home-route
   (wrap-login-redirect
    (routes attachment-routes
            redirects))
   (auth/auth-routes)
   #'api-routes
   ;; TODO should we disable logging of resource requests?
   (wrap-cacheable
    (routes
     styles/css-routes
     resource-handler
     (extra-script-routes (:extra-scripts env))
     (extra-stylesheet-routes (:extra-stylesheets env))
     (static-resources (:extra-static-resources env))
     (static-resources (:theme-static-resources env))
     webjar-handler))
   not-found-handler))

;; we use mount to construct the app so that middleware can access mount state
(mount/defstate handler
  :start (wrap-base (app-routes)))
