(ns rems.api.organizations
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.service.organizations :as organizations]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateOrganizationCommand
  (-> schema-base/OrganizationFull
      (dissoc :organization/modifier
              :organization/last-modifier)
      (assoc (s/optional-key :organization/owners) [schema-base/User])))

(s/defschema CreateOrganizationResponse
  {:success s/Bool
   (s/optional-key :organization/id) s/Str
   (s/optional-key :errors) [s/Any]})

(s/defschema EditOrganizationCommand CreateOrganizationCommand)

(s/defschema EditOrganizationResponse
  {:success s/Bool
   :organization/id s/Str
   (s/optional-key :errors) [s/Any]})

;; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableOwner schema-base/UserWithAttributes)
(s/defschema AvailableOwners [AvailableOwner])

(def organizations-api
  (context "/organizations" []
    :tags ["organizations"]

    (GET "/" []
      :summary "Get organizations. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :query-params [{owner :- (describe s/Str "return only organizations that are owned by owner") nil}
                     {disabled :- (describe s/Bool "whether to include disabled organizations") false}
                     {archived :- (describe s/Bool "whether to include archived organizations") false}]
      :return [schema-base/OrganizationFull]
      (ok (organizations/get-organizations (merge {:userid (getx-user-id)
                                                   :owner owner}
                                                  (when-not disabled {:enabled true})
                                                  (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create organization"
      :roles #{:owner}
      :body [command CreateOrganizationCommand]
      :return CreateOrganizationResponse
      (ok (organizations/add-organization! command)))

    (PUT "/edit" []
      :summary "Edit organization. Organization owners cannot change the owners."
      ;; explicit roles seem clearer here instead of +admin-write-roles+
      :roles #{:owner :organization-owner}
      :body [command EditOrganizationCommand]
      :return EditOrganizationResponse
      (ok (organizations/edit-organization! command)))

    (PUT "/archived" []
      :summary "Archive or unarchive the organization"
      :roles #{:owner}
      :body [command schema/OrganizationArchivedCommand]
      :return schema/SuccessResponse
      (ok (organizations/set-organization-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable the organization"
      :roles #{:owner}
      :body [command schema/OrganizationEnabledCommand]
      :return schema/SuccessResponse
      (ok (organizations/set-organization-enabled! command)))

    (GET "/available-owners" []
      :summary "List of available owners"
      :roles #{:owner :organization-owner}
      :return AvailableOwners
      (ok (organizations/get-available-owners)))

    (GET "/:organization-id" []
      :summary "Get an organization. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :path-params [organization-id :- (describe s/Str "organization id")]
      :return schema-base/OrganizationFull
      (if-let [org (organizations/get-organization (getx-user-id) {:organization/id organization-id})]
        (ok org)
        (not-found-json-response)))))
