(ns rems.api.applications

  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.applications :as applications]
            [ring.util.http-response :refer :all]))

(def GetApplicationsResponse
  [Application])

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get current user's all applications"
      :return GetApplicationsResponse
      (check-user)
      (ok (applications/get-my-applications)))))
