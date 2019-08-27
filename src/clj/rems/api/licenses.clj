(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :as api-util] ; required for route :roles
            [rems.db.attachments :as attachments]
            [rems.db.core :as db]
            [rems.api.services.licenses :as licenses]
            [rems.util :refer [getx-user-id]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateLicenseCommand
  {:licensetype (s/enum "link" "text" "attachment")
   :title s/Str
   :textcontent s/Str
   (s/optional-key :attachment-id) (s/maybe s/Int)
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/optional-key :attachment-id) (s/maybe s/Int)}}})

(s/defschema AttachmentMetadata
  {:id s/Int})

(s/defschema CreateLicenseResponse
  {:success s/Bool
   :id s/Int})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :roles #{:handler :owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled licenses") false}
                     {archived :- (describe s/Bool "whether to include archived licenses") false}]
      :return Licenses
      (ok (licenses/get-all-licenses (merge (when-not disabled {:enabled true})
                                            (when-not archived {:archived false})))))

    (GET "/:license-id" []
      :summary "Get license"
      :roles #{:owner}
      :path-params [license-id :- (describe s/Int "license id")]
      :return License
      (ok (licenses/get-license license-id)))

    (POST "/create" []
      :summary "Create license"
      :roles #{:owner}
      :body [command CreateLicenseCommand]
      :return CreateLicenseResponse
      (ok (licenses/create-license! command (getx-user-id))))

    (PUT "/update" []
      :summary "Update workflow"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (licenses/update-license! command)))

    (POST "/add_attachment" []
      :summary "Add an attachment file that will be used in a license"
      :roles #{:owner}
      :multipart-params [file :- upload/TempFileUpload]
      :middleware [multipart/wrap-multipart-params]
      :return AttachmentMetadata
      (attachments/check-attachment-content-type (:content-type file))
      (ok (licenses/create-license-attachment! file (getx-user-id))))

    (POST "/remove_attachment" []
      :summary "Remove an attachment that could have been used in a license."
      :roles #{:owner}
      :query-params [attachment-id :- (describe s/Int "attachment id")]
      :return SuccessResponse
      (if (some? (licenses/remove-license-attachment! attachment-id))
        (ok {:success true})
        (ok {:success false})))

    (GET "/attachments/:attachment-id" []
      :summary "Get a license's attachment"
      :roles #{:owner}
      :path-params [attachment-id :- (describe s/Int "attachment id")]
      (if-let [attachment (db/get-license-attachment {:attachmentId attachment-id})]
        (do (attachments/check-attachment-content-type (:type attachment))
            (-> (:data attachment)
                (java.io.ByteArrayInputStream.)
                (ok)
                (content-type (:type attachment))))
        (api-util/not-found-json-response)))))
