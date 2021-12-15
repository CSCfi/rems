(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.util] ; required for route :roles
            [rems.auth.util :refer [throw-forbidden throw-unauthorized]]
            [rems.config :refer [env]]
            [rems.common.roles :as roles]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueResponse
  [schema/CatalogueItem])

(s/defschema GetCatalogueTreeResponse
  {:roots [(s/either schema/CategoryTree schema/CatalogueItem)]})

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]
    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :return GetCatalogueResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (catalogue/get-localized-catalogue-items {:archived false}))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))

    (GET "/tree" []
      :summary "Get the catalogue of items in a tree for the UI (does not include archived items) (EXPERIMENTAL)"
      :return GetCatalogueTreeResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (catalogue/get-catalogue-tree {:archived false :expand-catalogue-data? true}))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))))
