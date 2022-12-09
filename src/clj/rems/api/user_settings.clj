(ns rems.api.user-settings
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.config :refer [env]]
            [rems.service.ega :as ega]
            [rems.service.user-settings :as user-settings]
            [rems.util :refer [getx-user-id get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(def GetUserSettings user-settings/UserSettings)

(s/defschema UpdateUserSettings
  {(s/optional-key :language) s/Keyword
   (s/optional-key :notification-email) (s/maybe s/Str)})

(s/defschema GenerateEGAApiKeyResponse
  {:success s/Bool
   (s/optional-key :api-key-expiration-date) DateTime})

(s/defschema DeleteEGAApiKeyResponse
  {:success s/Bool})

(def user-settings-api
  (context "/user-settings" []
    :tags ["user-settings"]

    (GET "/" []
      :summary "Get user settings"
      :roles #{:logged-in}
      :return GetUserSettings
      (ok (user-settings/get-user-settings (get-user-id))))

    (PUT "/edit" []
      :summary "Update user settings"
      :roles #{:logged-in}
      :body [settings UpdateUserSettings]
      :return schema/SuccessResponse
      (ok (user-settings/update-user-settings! (getx-user-id) settings)))

    (PUT "/" []
      :summary "Update user settings, DEPRECATED, will disappear, use /edit instead"
      :roles #{:logged-in}
      :body [settings UpdateUserSettings]
      :return schema/SuccessResponse
      (ok (user-settings/update-user-settings! (getx-user-id) settings)))

    (POST "/generate-ega-api-key" [:as request] ; NB: binding syntax
      :summary "Generates a new EGA API-key for the user."
      :roles #{:handler}
      :return GenerateEGAApiKeyResponse
      (if-not (:enable-ega env)
        (not-implemented "EGA not enabled")
        (let [access-token (get-in request [:session :access-token])]
          (ok (ega/generate-api-key-with-access-token {:userid (get-user-id)
                                                       :access-token access-token
                                                       :config (ega/get-ega-config)})))))

    (POST "/delete-ega-api-key" [:as request] ; NB: binding syntax
      :summary "Deletes the EGA API-key of the user."
      :roles #{:handler}
      :return DeleteEGAApiKeyResponse
      (if-not (:enable-ega env)
        (not-implemented "EGA not enabled")
        (let [access-token (get-in request [:session :access-token])]
          (ok (ega/delete-api-key {:userid (get-user-id)
                                   :access-token access-token
                                   :config (ega/get-ega-config)})))))))
