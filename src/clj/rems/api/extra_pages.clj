(ns rems.api.extra-pages
  (:require [clojure.java.io :as io]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.config :refer [env]]
            [rems.common-util :refer [index-by]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (java.io FileNotFoundException)))

(s/defschema ExtraPage
  {s/Keyword s/Str})

(defn- get-extra-page [page-id]
  (let [allowed-ids (index-by [:id] (filter #(not (:url %)) (:extra-pages env)))]
    (when (contains? allowed-ids page-id)
      (let [translations (get-in allowed-ids [page-id :translations])]
        (into
         {}
         (for [lang (keys translations)]
           (let [filename (get-in translations [lang :filename])
                 extra-pages-path (:extra-pages-path env)
                 _ (assert extra-pages-path ":extra-pages-path undefined in config")
                 file (io/file extra-pages-path filename)]
             (if (.isFile file)
               [lang (slurp file)]
               (throw (FileNotFoundException. (str "the file specified in extra-pages does not exist: " file)))))))))))

(def extra-pages-api
  (context "/extra-pages" []
    :tags ["extra-pages"]

    (GET "/:page-id" []
      :summary "Return all translations for a given extra page"
      :path-params [page-id :- (describe s/Str "page-id")]
      :return ExtraPage
      (ok (get-extra-page page-id)))))
