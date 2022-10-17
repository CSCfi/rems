(ns rems.api.extra-pages
  (:require [clojure.java.io :as io]
            [compojure.api.sweet :refer :all]
            [medley.core :refer [find-first]]
            [rems.api.util :as api-util]
            [rems.config :refer [env]]
            [rems.common.util :refer [index-by]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (java.io FileNotFoundException)))

(s/defschema ExtraPageResponse
  {s/Keyword (s/maybe s/Str)})

(defn- get-extra-page [page-id]
  (let [extra-pages (:extra-pages env)]
    (when-let [page (find-first (comp #{page-id} :id) extra-pages)]
      (let [translations (:translations page)
            extra-pages-path (:extra-pages-path env)]
        (assert extra-pages-path ":extra-pages-path undefined in config")
        (into {}
              (for [[lang {:keys [filename]}] translations
                    :let [filename (or filename (:filename page))]]
                [lang (when filename
                        (let [file (io/file extra-pages-path filename)]
                          (when (.isFile file) (slurp file))))]))))))

(def extra-pages-api
  (context "/extra-pages" []
    :tags ["extra-pages"]

    (GET "/:page-id" []
      :summary "Return all translations for a given extra page"
      :path-params [page-id :- (describe s/Str "page-id")]
      :responses {200 {:schema ExtraPageResponse}
                  404 {:schema s/Any :description "Not found"}}
      (if-let [response (get-extra-page page-id)]
        (ok response)
        (api-util/not-found-json-response)))))
