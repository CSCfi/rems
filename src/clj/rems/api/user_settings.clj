(ns rems.api.user-settings
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.db.user-settings :as user-settings]
            [rems.util :refer [getx-user-id get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema GetUserSettings
  {:language s/Keyword
   :notification-email (s/maybe s/Str)
   (s/optional-key :ega) {:api-key-expiration-date DateTime}})

(s/defschema UpdateUserSettings
  {(s/optional-key :language) s/Keyword
   (s/optional-key :notification-email) (s/maybe s/Str)})

(def user-settings-api
  (context "/user-settings" []
    :tags ["user-settings"]

    (GET "/" []
      :summary "Get user settings"
      :roles #{:logged-in}
      :return GetUserSettings
      (ok (user-settings/get-user-settings (get-user-id))))

    (PUT "/" []
      :summary "Update user settings"
      :roles #{:logged-in}
      :body [settings UpdateUserSettings]
      :return schema/SuccessResponse
      (ok (user-settings/update-user-settings! (getx-user-id) settings)))))
