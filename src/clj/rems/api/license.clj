(ns rems.api.license
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all])
  (:import [org.joda.time DateTime]))

(def license-api
  (context "/license" []
    :tags ["license"]

    (GET "/" []
      :summary "Get licenses"
      :return [License]
      (check-user)
      (check-roles :owner)
      (ok (licenses/get-all-licenses)))))
