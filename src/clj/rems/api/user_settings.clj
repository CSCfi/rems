(ns rems.api.user-settings
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.db.user-settings :as user-settings]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema UserSettings
  {:language s/Keyword})

(def user-settings-api
  (context "/user-settings" []
    :tags ["user-settings"]

    (GET "/" []
      :summary "Get user settings"
      :roles #{:logged-in}
      :return UserSettings
      (ok (user-settings/get-user-settings (getx-user-id))))

    (PUT "/" []
      :summary "Update user settings"
      :roles #{:logged-in}
      :body [settings UserSettings]
      :return SuccessResponse
      (ok (user-settings/update-user-settings! (getx-user-id) settings)))))
