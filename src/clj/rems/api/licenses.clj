(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateLicenseCommand
  {:licensetype (s/enum "link" "text" "attachment")
   :title s/Str
   :textcontent s/Str
   (s/->OptionalKey :attachment) s/Any
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/->OptionalKey :attachment) s/Any}}})

(s/defschema CreateLicenseResponse
  {:id s/Num})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :roles #{:owner}
      :query-params [{active :- (describe s/Bool "filter active or inactive licenses") nil}]
      :return Licenses
      (ok (licenses/get-all-licenses (when-not (nil? active) {:active? active}))))

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
      (ok (licenses/create-license! command)))))
