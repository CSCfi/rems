(ns rems.api.organizations
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.organizations :as organizations]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def organizations-api
  (context "/organizations" []
    :tags ["organizations"]

    (GET "/" []
      :summary "Get organizations"
      :roles #{:owner :organization-owner :handler}
      :query-params [{owner :- (describe s/Str "return only organizations that are owned by owner") nil}]
      :return [Organization]
      (ok (organizations/get-organizations owner)))))
