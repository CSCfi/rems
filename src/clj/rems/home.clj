(ns rems.home
  (:require [compojure.core :refer [GET defroutes]]
            [markdown.core :as md]
            [rems.auth.auth :as auth]
            [rems.auth.util :as auth-util]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.css.styles :as styles]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.util :refer [index-by]]
            [ring.util.response :refer [content-type
                                        redirect
                                        response]]))

(defn- find-allowed-markdown-file [filename]
  (let [allowed-files (index-by [:file] (filter :file (:extra-pages env)))]
    (when (contains? allowed-files filename)
      (allowed-files filename))))

(defn- markdown-page [filename]
  (if-let [allowed-file (find-allowed-markdown-file filename)]
    (layout/render filename (md/md-to-html-string (slurp (:file allowed-file))))
    (auth-util/throw-unauthorized)))

(defroutes home-routes
  (GET "/markdown/:filename" [filename] (markdown-page filename))
  (GET "/css/screen.css" [] (-> (styles/generate-css)
                                (response)
                                (content-type "text/css"))))
