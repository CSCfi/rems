(ns rems.api.licenses
  (:require [rems.api.schema :refer :all]
            [rems.api.util :as api-util]
            [rems.db.licenses :as licenses]
            [rems.util :refer [getx-user-id]]
            [rems.db.core :as db]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]))

(s/defschema CreateLicenseCommand
  {:licensetype (s/enum "link" "text" "attachment")
   :title s/Str
   :textcontent s/Str
   (s/optional-key :attachment-id) (s/maybe s/Num)
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/optional-key :attachment-id) (s/maybe s/Num)}}})

(s/defschema AttachmentMetadata
  {:id s/Num})

(defn- check-attachment-content-type
  "Checks that content-type matches the allowed ones listed on the UI side:
   .pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
  [content-type]
  (when-not (or (#{"application/pdf"
                   "application/msword"
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                   "application/vnd.ms-powerpoint"
                   "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                   "text/plain"}
                 content-type)
                (.startsWith content-type "image/"))
    (throw (rems.InvalidRequestException. (str "Unsupported content-type: " content-type)))))

(s/defschema CreateLicenseResponse
  {:success s/Bool
   :id s/Num})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled licenses") false}
                     {expired :- (describe s/Bool "whether to include expired licenses") false}
                     {archived :- (describe s/Bool "whether to include archived licenses") false}]
      :return Licenses
      (ok (licenses/get-all-licenses (merge (when-not disabled {:enabled true})
                                            (when-not expired {:expired false})
                                            (when-not archived {:archived false})))))

    (GET "/:license-id" []
      :summary "Get license"
      :roles #{:owner}
      :path-params [license-id :- (describe s/Num "license id")]
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
      (check-attachment-content-type (:content-type file))
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
        (do (check-attachment-content-type (:type attachment))
            (-> (:data attachment)
                (java.io.ByteArrayInputStream.)
                (ok)
                (content-type (:type attachment))))
        (api-util/not-found-json-response)))))
