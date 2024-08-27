(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.attachment :as attachment]
            [rems.service.licenses :as licenses]
            [rems.api.util :refer [extended-logging not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.swagger.json-schema :as rjs]
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

(s/defschema AddLicenseAttachmentResponse
  {:success s/Bool
   :id s/Int})

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
      (ok (rems.service.licenses/get-all-licenses (merge (when-not disabled {:enabled true})
                                                         (when-not archived {:archived false})))))

    (GET "/:license-id" []
      :summary "Get license"
      :roles +admin-read-roles+
      :path-params [license-id :- (describe s/Int "license id")]
      :return schema/License
      (if-let [license (licenses/get-license license-id)]
        (ok license)
        (not-found-json-response)))

    (POST "/create" request
      :summary "Create license"
      :roles +admin-write-roles+
      :body [command CreateLicenseCommand]
      :return CreateLicenseResponse
      (extended-logging request)
      (ok (licenses/create-license! command)))

    (PUT "/archived" request
      :summary "Archive or unarchive license"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (licenses/set-license-archived! command)))

    (PUT "/enabled" request
      :summary "Enable or disable license"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (licenses/set-license-enabled! command)))

    (POST "/add_attachment" request
      :summary "Add an attachment file that will be used in a license"
      :roles +admin-write-roles+
      :multipart-params [file :- schema/FileUpload]
      :return AddLicenseAttachmentResponse
      (extended-logging request)
      (ok (rems.service.attachment/create-license-attachment! {:file file
                                                               :user-id (getx-user-id)})))

    (POST "/remove_attachment" request
      :summary "Remove an attachment that could have been used in a license."
      :roles +admin-write-roles+
      :query-params [attachment-id :- (describe s/Int "attachment id")]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.attachment/remove-license-attachment! attachment-id)))

    (GET "/attachments/:attachment-id" []
      :summary "Get a license's attachment"
      :roles +admin-write-roles+
      :path-params [attachment-id :- (describe s/Int "attachment id")]
      (if-let [attachment (rems.service.attachment/get-license-attachment attachment-id)]
        (rems.service.attachment/download attachment)
        (not-found-json-response)))))
