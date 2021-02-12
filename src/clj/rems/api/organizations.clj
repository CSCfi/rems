(ns rems.api.organizations
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer [OrganizationArchivedCommand OrganizationEnabledCommand OrganizationFull SuccessResponse UserWithAttributes]]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.api.services.organizations :as organizations]
            [rems.schema-base :refer [User]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateOrganizationCommand
  (-> OrganizationFull
      (dissoc :organization/modifier
              :organization/last-modifier)
      (assoc (s/optional-key :organization/owners) [User])))

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
(s/defschema AvailableOwner UserWithAttributes)
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
      :return [OrganizationFull]
      (ok (organizations/get-organizations (merge {:userid (getx-user-id)
                                                   :owner owner}
                                                  (when-not disabled {:enabled true})
                                                  (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create organization"
      :roles #{:owner}
      :body [command CreateOrganizationCommand]
      :return CreateOrganizationResponse
      (ok (organizations/add-organization! (getx-user-id) command)))

    (PUT "/edit" []
      :summary "Edit organization. Organization owners cannot change the owners."
      ;; explicit roles seem clearer here instead of +admin-write-roles+
      :roles #{:owner :organization-owner}
      :body [command EditOrganizationCommand]
      :return EditOrganizationResponse
      (ok (organizations/edit-organization! (getx-user-id) command)))

    (PUT "/archived" []
      :summary "Archive or unarchive the organization"
      :roles #{:owner}
      :body [command OrganizationArchivedCommand]
      :return SuccessResponse
      (ok (organizations/set-organization-archived! (getx-user-id) command)))

    (PUT "/enabled" []
      :summary "Enable or disable the organization"
      :roles #{:owner}
      :body [command OrganizationEnabledCommand]
      :return SuccessResponse
      (ok (organizations/set-organization-enabled! (getx-user-id) command)))

    (GET "/available-owners" []
      :summary "List of available owners"
      :roles #{:owner :organization-owner}
      :return AvailableOwners
      (ok (organizations/get-available-owners)))

    (GET "/:organization-id" []
      :summary "Get an organization. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :path-params [organization-id :- (describe s/Str "organization id")]
      :return OrganizationFull
      (if-let [org (organizations/get-organization (getx-user-id) {:organization/id organization-id})]
        (ok org)
        (not-found-json-response)))))
