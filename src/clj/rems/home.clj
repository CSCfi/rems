(ns rems.home
  (:require [compojure.core :refer [GET defroutes]]
            [markdown.core :as md]
            [rems.auth.util :as auth-util]
            [rems.common-util :refer [index-by]]
            [rems.config :refer [env]]
            [rems.css.styles :as styles]
            [rems.db.catalogue :as catalogue]
            [rems.layout :as layout]
            [ring.util.response :refer [content-type not-found redirect response]]))

(defn- apply-for-resource [resource]
  (let [items (->> (catalogue/get-localized-catalogue-items {:resource resource})
                   (remove catalogue/disabled-catalogue-item?))]
    (cond
      (= 0 (count items)) (not-found "Resource not found")
      (< 1 (count items)) (not-found "Resource ID is not unique")
      :else (redirect (str "/#/application?items=" (:id (first items)))))))

(defn- find-allowed-markdown-file [filename]
  (let [allowed-files (index-by [:file] (filter :file (:extra-pages env)))]
    (when (contains? allowed-files filename)
      (allowed-files filename))))

(defn- markdown-page [filename]
  (if-let [allowed-file (find-allowed-markdown-file filename)]
    (layout/render filename (md/md-to-html-string (slurp (:file allowed-file))))
    (auth-util/throw-unauthorized)))

(defroutes home-routes
  (GET "/" [] (layout/home-page))
  (GET "/apply-for" {{:keys [resource]} :params} (apply-for-resource resource))
  (GET "/landing_page" req (redirect "/#/redirect")) ; DEPRECATED: legacy url redirect
  (GET "/markdown/:filename" [filename] (markdown-page filename))
  (GET "/css/screen.css" [] (-> styles/screen
                                (response)
                                (content-type "text/css"))))
