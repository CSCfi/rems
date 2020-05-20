(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.util] ; required for route :roles
            [rems.api.util :refer [check-roles]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.config :refer [env]]
            [rems.roles :as roles]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueResponse
  [CatalogueItem])

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]
    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :return GetCatalogueResponse
      (if (or (:catalogue-is-public env)
              (roles/has-roles? :logged-in))
        (ok (catalogue/get-localized-catalogue-items {:archived false}))
        (throw-forbidden)))))
