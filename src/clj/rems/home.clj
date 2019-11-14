(ns rems.home
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes routes]]
            [rems.api.services.attachment :as attachment]
            [rems.api.util :as api-util]
            [rems.context :as context]
            [rems.css.styles :as styles]
            [rems.db.catalogue :as catalogue]
            [rems.layout :as layout]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [content-type not-found redirect response]]))

(defn- apply-for-resource [resource]
  (let [items (->> (catalogue/get-localized-catalogue-items {:resource resource})
                   (filter :enabled))]
    (cond
      (= 0 (count items)) (-> (not-found "Resource not found")
                              (content-type "text/plain"))
      (< 1 (count items)) (-> (not-found "Resource ID is not unique")
                              (content-type "text/plain"))
      :else (redirect (str "/application?items=" (:id (first items)))))))

(defn render-css
  "Helper function for rendering styles that has parameters for
  easy memoization purposes."
  [language]
  (log/info (str "Rendering stylesheet for language " language))
  (-> (styles/screen-css)
      (response)
      (content-type "text/css")))

(def memoized-render-css (memoize render-css))

(defroutes normal-routes
  (GET "/" []
    (layout/home-page))

  (GET "/accept-invitation" [token]
    (redirect (str "/application/accept-invitation/" token)))

  (GET "/apply-for" [resource]
    (apply-for-resource resource))

  (GET "/applications/attachment/:attachment-id" [attachment-id]
    (let [attachment-id (Long/parseLong attachment-id)]
      (if-let [user-id (get-user-id)]
        (if-let [attachment (attachment/get-application-attachment user-id attachment-id)]
          (attachment/download attachment)
          (api-util/not-found-text-response))
        ;; TODO: this redirect could be generic middleware
        (redirect (str "/?redirect=/applications/attachment/" attachment-id)))))

  (GET "/landing_page" [] ; DEPRECATED: legacy url redirect
    (redirect "/redirect"))

  (GET "/favicon.ico" []
    (redirect "/img/favicon.ico")))

(defroutes css-routes
  (GET "/css/:language/screen.css" [language]
    (binding [context/*lang* (keyword language)]
      (memoized-render-css context/*lang*))))

(defn home-routes []
  (routes normal-routes
          css-routes))
