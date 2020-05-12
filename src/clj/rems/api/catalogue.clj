(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.catalogue :as catalogue]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

            ; [rems.config :refer [env]]
            ; [rems.api.util/check-roles :as check-roles ]

(s/defschema GetCatalogueResponse
  [CatalogueItem])

; (def catalogue-api
;   (context "/catalogue" []
;     :tags ["catalogue"]
;     (GET "/" []
;       :summary "Get the catalogue of items for the UI (does not include archived items)"
;       :return GetCatalogueResponse
;         (ok (catalogue/get-localized-catalogue-items {:archived false})))))

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]

    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :roles #{:logged-in}
      :return GetCatalogueResponse
      (ok (catalogue/get-localized-catalogue-items {:archived false})))))