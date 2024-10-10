(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.catalogue]
            [rems.api.util] ; required for route :roles
            [rems.auth.util :refer [throw-forbidden throw-unauthorized]]
            [rems.config :refer [env]]
            [rems.common.roles :as roles]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueResponse
  [schema/CatalogueItem])

(s/defschema GetCatalogueTreeResponse
  {:roots [(s/either schema/CategoryTree
                     schema/CatalogueItem)]}) ; catalogue items without categories end up on the root level

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]
    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :query-params [{join-organization :- (describe s/Bool "Should organizations be returned for each item?") true}]
      :return GetCatalogueResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (rems.service.catalogue/get-catalogue-table {:join-organization? join-organization}))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))

    (GET "/tree" []
      :summary "Get the catalogue of items in a tree for the UI (does not include archived items)"
      :query-params [{join-organization :- (describe s/Bool "Should organizations be returned for each item?") true}]
      :return GetCatalogueTreeResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (rems.service.catalogue/get-catalogue-tree {:join-organization? join-organization
                                                        :empty false}))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))))
