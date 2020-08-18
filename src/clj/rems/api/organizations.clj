(ns rems.api.organizations
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.api.services.organizations :as organizations]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateOrganizationCommand
  OrganizationFull)

(s/defschema CreateOrganizationResponse
  {:success s/Bool
   (s/optional-key :organization/id) s/Str
   (s/optional-key :errors) [s/Any]})

(def organizations-api
  (context "/organizations" []
    :tags ["organizations"]

    (GET "/" []
      :summary "Get organizations. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :query-params [{owner :- (describe s/Str "return only organizations that are owned by owner") nil}
                     {disabled :- (describe s/Bool "whether to include disabled organizations") false}
                     {archived :- (describe s/Bool "whether to include archived organizations") false}]
      :return [OrganizationFull]
      (ok (organizations/get-organizations (merge {:userid (getx-user-id)
                                                   :owner owner}
                                                  (when-not disabled {:enabled true})
                                                  (when-not archived {:archived false})))))

    (GET "/:organization-id" []
      :summary "Get an organization. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :path-params [organization-id :- (describe s/Str "organization id")]
      :return OrganizationFull
      (ok (organizations/get-organization (getx-user-id) {:organization/id organization-id})))

    (POST "/create" []
      :summary "Create organization"
      :roles #{:owner}
      :body [command CreateOrganizationCommand]
      :return CreateOrganizationResponse
      (ok (organizations/add-organization! (getx-user-id) command)))

    (PUT "/archived" []
      :summary "Archive or unarchive the organization"
      :roles #{:owner}
      :body [command OrganizationArchivedCommand]
      :return SuccessResponse
      (ok (organizations/set-organization-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable the organization"
      :roles #{:owner}
      :body [command OrganizationEnabledCommand]
      :return SuccessResponse
      (ok (organizations/set-organization-enabled! command)))))
