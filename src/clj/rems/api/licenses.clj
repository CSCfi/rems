(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.attachment :as attachment]
            [rems.service.licenses :as licenses]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.json-schema :as rjs]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema LicenseLocalization
  {:title s/Str
   :textcontent s/Str
   (s/optional-key :attachment-id) (rjs/describe (s/maybe s/Int) "For licenses of type attachment")})

(s/defschema LicenseLocalizations
  (rjs/field {schema-base/Language LicenseLocalization}
             {:description "Licence localizations keyed by language"
              :example {:en {:title "English title"
                             :textcontent "English content"}
                        :fi {:title "Finnish title"
                             :textcontent "Finnish content"}}}))

(s/defschema CreateLicenseCommand
  {:licensetype (s/enum "link" "text" "attachment")
   :organization schema-base/OrganizationId
   :localizations LicenseLocalizations})

(s/defschema AttachmentMetadata
  {:id s/Int})

(s/defschema CreateLicenseResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :roles +admin-read-roles+
      :query-params [{disabled :- (describe s/Bool "whether to include disabled licenses") false}
                     {archived :- (describe s/Bool "whether to include archived licenses") false}]
      :return schema/Licenses
      (ok (licenses/get-all-licenses (merge (when-not disabled {:enabled true})
                                            (when-not archived {:archived false})))))

    (GET "/:license-id" []
      :summary "Get license"
      :roles +admin-read-roles+
      :path-params [license-id :- (describe s/Int "license id")]
      :return schema/License
      (if-let [license (licenses/get-license license-id)]
        (ok license)
        (not-found-json-response)))

    (POST "/create" []
      :summary "Create license"
      :roles +admin-write-roles+
      :body [command CreateLicenseCommand]
      :return CreateLicenseResponse
      (ok (licenses/create-license! command)))

    (PUT "/archived" []
      :summary "Archive or unarchive license"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (ok (licenses/set-license-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable license"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (ok (licenses/set-license-enabled! command)))

    (POST "/add_attachment" []
      :summary "Add an attachment file that will be used in a license"
      :roles +admin-write-roles+
      :multipart-params [file :- schema/FileUpload]
      :return AttachmentMetadata
      (ok (licenses/create-license-attachment! file (getx-user-id))))

    (POST "/remove_attachment" []
      :summary "Remove an attachment that could have been used in a license."
      :roles +admin-write-roles+
      :query-params [attachment-id :- (describe s/Int "attachment id")]
      :return schema/SuccessResponse
      (ok {:success (some? (licenses/remove-license-attachment! attachment-id))}))

    (GET "/attachments/:attachment-id" []
      :summary "Get a license's attachment"
      :roles +admin-write-roles+
      :path-params [attachment-id :- (describe s/Int "attachment id")]
      (if-let [attachment (licenses/get-license-attachment attachment-id)]
        (attachment/download attachment)
        (not-found-json-response)))))
