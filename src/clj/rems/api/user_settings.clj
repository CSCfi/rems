(ns rems.api.user-settings
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.db.user-settings :as user-settings]
            [rems.util :refer [getx-user-id get-user-id]]
            [ring.util.http-response :refer :all]))

(def user-settings-api
  (context "/user-settings" []
    :tags ["user-settings"]

    (GET "/" []
      :summary "Get user settings"
      :return user-settings/UserSettings
      (ok (user-settings/get-user-settings (get-user-id))))

    (PUT "/" []
      :summary "Update user settings"
      :roles #{:logged-in}
      :body [settings user-settings/PartialUserSettings]
      :return schema/SuccessResponse
      (ok (user-settings/update-user-settings! (getx-user-id) settings)))))
