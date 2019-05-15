(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueResponse
  [CatalogueItem])

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]

    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :roles #{:logged-in}
      :return GetCatalogueResponse
      (ok (catalogue/get-localized-catalogue-items {:archived false})))))
