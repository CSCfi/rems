(ns rems.api.reviews
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :refer [get-open-reviews-v2 get-handled-reviews-v2]]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def reviews-api
  (context "/v2/reviews" []
    :tags ["reviews"]

    (GET "/open" []
      :summary "Lists applications which the user needs to review"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [ApplicationOverview]
      (ok (get-open-reviews-v2 (getx-user-id))))

    (GET "/handled" []
      :summary "Lists applications which the user has already reviewed"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [ApplicationOverview]
      (ok (get-handled-reviews-v2 (getx-user-id))))))
