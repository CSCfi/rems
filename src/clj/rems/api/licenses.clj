(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateLicenseCommand
  {:licensetype (s/enum "link" "text")
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str}}})

(s/defschema CreateLicenseResponse
  {:id s/Num})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :roles #{:owner}
      :query-params [{active :- (describe s/Bool "filter active or inactive licenses") nil}]
      :return [License]
      (ok (licenses/get-all-licenses (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create license"
      :roles #{:owner}
      :body [command CreateLicenseCommand]
      :return CreateLicenseResponse
      (ok (licenses/create-license! command)))))
