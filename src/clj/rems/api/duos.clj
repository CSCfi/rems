(ns rems.api.duos
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.services.duo :as duo]
            [rems.common.roles :refer [+admin-read-roles+]]
            [ring.util.http-response :refer :all]))

(def duos-api
  (context "/duos" []
    :tags ["duos"]

    (GET "/" []
      :summary "Get DUO codes"
      :roles +admin-read-roles+
      :return schema/DuoCodes
      (ok (duo/get-all-duo-codes)))

    (GET "/mondo-codes" []
      :summary "Get all MONDO codes"
      :roles +admin-read-roles+
      :return schema/MondoCodes
      (ok duo/mondo-codes))))
