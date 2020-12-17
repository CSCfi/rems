(ns rems.handler
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [rems.api :refer [api-routes]]
            [rems.api.services.attachment :as attachment]
            [rems.api.services.licenses :as licenses]
            [rems.api.util :as api-util]
            [rems.context :as context]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.css.styles :as styles]
            [rems.db.catalogue :as catalogue]
            [rems.email.core] ;; to enable email polling
            [rems.entitlements :as entitlements]
            [rems.layout :as layout]
            [rems.middleware :as middleware]
            [rems.util :refer [getx-user-id never-match-route]]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [content-type file-response not-found bad-request redirect response]])
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

(defn render-css
  "Helper function for rendering styles that has parameters for
  easy memoization purposes."
  [language]
  (log/info (str "Rendering stylesheet for language " language))
  (-> (styles/screen-css)
      (response)
      (content-type "text/css")))

(mount/defstate memoized-render-css
  :start (memoize render-css))

(defroutes home-normal-routes
  (GET "/" []
    (layout/home-page))

  ;; TODO should these redirects have swagger documentation?

  (GET "/accept-invitation" [token]
    (redirect (str "/application/accept-invitation/" token)))

  (GET "/apply-for" [resource] ;; can specify multiple resources
    (if (vector? resource)
      (apply-for-resources resource)
      (apply-for-resources [resource])))

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
        (api-util/not-found-text-response))))

  (GET "/landing_page" [] ; DEPRECATED: legacy url redirect
    (redirect "/redirect"))

  (GET "/favicon.ico" []
    (redirect "/img/favicon.ico")))

(defroutes css-routes
  (GET "/css/:language/screen.css" [language]
    (binding [context/*lang* (keyword language)]
      (memoized-render-css context/*lang*))))

(defn wrap-login-redirect [handler]
  (fn [req]
    (try
      (handler req)
      (catch UnauthorizedException _
        (redirect (str "/?redirect=" (url-encode (:uri req))))))))

(defn home-routes []
  (routes (-> home-normal-routes
              wrap-login-redirect)
          css-routes))

(defn not-found-handler [_req]
  ;; TODO: serve 404 for routes which the frontend doesn't recognize
  #_(layout/error-page {:status 404
                        :title "Page not found"})
  (layout/home-page))

(defn public-routes []
  (routes
   (home-routes)
   ;; never cache authentication results
   ;; TODO this is a slightly hacky place to do this
   (middleware/wrap-no-cache (auth/auth-routes))))

(defroutes secured-routes
  entitlements/entitlements-routes)

(defn normal-routes []
  (routes
   (public-routes)
   (wrap-routes #'secured-routes middleware/wrap-restricted)
   #'api-routes))

(defn extra-script-routes [{:keys [root files]}]
  (let [files (set files)]
    (fn [request]
      (when (contains? files (:uri request))
        (file-response (:uri request) {:root root})))))

(defn app-routes []
  (routes
   (extra-script-routes (:extra-scripts env))
   (normal-routes)
   (if-let [path (:extra-static-resources env)]
     (route/files "/" {:root path})
     never-match-route)
   (if-let [path (:theme-static-resources env)]
     (route/files "/" {:root path})
     never-match-route)
   not-found-handler))

;; we use mount to construct the app so that middleware can access mount state
(mount/defstate handler
  :start (middleware/wrap-base (app-routes)))
