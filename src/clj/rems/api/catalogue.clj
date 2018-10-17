(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.catalogue :as catalogue]
            [ring.util.http-response :refer :all]))

(def GetCatalogueResponse
  [CatalogueItem])

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]

    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include disabled) (roles: all)"
      :return GetCatalogueResponse
      (check-user)
      (ok (catalogue/get-localized-catalogue-items)))))
