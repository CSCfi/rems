(ns rems.api.extra-pages
  (:require [clojure.java.io :as io]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.config :refer [env]]
            [rems.common-util :refer [index-by]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (java.io FileNotFoundException)))

(s/defschema ExtraPageResponse
  {s/Keyword s/Str})

(defn- get-extra-page [page-id]
  (let [allowed-ids (index-by [:id] (filter #(not (:url %)) (:extra-pages env)))]
    (when (contains? allowed-ids page-id)
      (let [translations (get-in allowed-ids [page-id :translations])
            extra-pages-path (:extra-pages-path env)]
        (assert extra-pages-path ":extra-pages-path undefined in config")
        (into
         {}
         (for [[lang {:keys [filename]}] translations]
           (let [file (io/file extra-pages-path filename)]
             (if (.isFile file)
               [lang (slurp file)]
               (throw (FileNotFoundException. (str "the file specified in extra-pages does not exist: " file)))))))))))

(def extra-pages-api
  (context "/extra-pages" []
    :tags ["extra-pages"]

    (GET "/:page-id" []
      :summary "Return all translations for a given extra page"
      :path-params [page-id :- (describe s/Str "page-id")]
      :return ExtraPageResponse
      (ok (get-extra-page page-id)))))
