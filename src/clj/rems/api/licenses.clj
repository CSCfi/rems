(ns rems.api.licenses
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def CreateLicenseCommand
  {:licensetype (s/enum "link" "text")
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str}}})

(def licenses-api
  (context "/licenses" []
    :tags ["licenses"]

    (GET "/" []
      :summary "Get licenses"
      :query-params [{active :- (describe s/Bool "filter active or inactive licenses") nil}]
      :return [License]
      (check-user)
      (check-roles :owner)
      (ok (licenses/get-all-licenses (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create license"
      :body [command CreateLicenseCommand]
      (check-user)
      (check-roles :owner)
      (ok (licenses/create-license! command)))))
