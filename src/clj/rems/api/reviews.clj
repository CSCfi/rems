(ns rems.api.reviews
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :refer [get-all-applications-v2]]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))


(defn- review? [application]
  (and (some #{:handler
               :commenter
               :past-commenter
               :decider
               :past-decider}
             (:application/roles application))
       (not= :application.state/draft (:application/state application))))

(defn get-all-reviews [user-id]
  (->> (get-all-applications-v2 user-id)
       (filter review?)))

(defn- open-review? [application]
  (some #{:application.command/approve
          :application.command/comment
          :application.command/decide}
        (:application/permissions application)))

(defn get-open-reviews [user-id]
  (->> (get-all-reviews user-id)
       (filter open-review?)))

(defn get-handled-reviews [user-id]
  (->> (get-all-reviews user-id)
       (remove open-review?)))

(def reviews-api
  (context "/v2/reviews" []
    :tags ["reviews"]

    (GET "/open" []
      :summary "Lists applications which the user needs to review"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [ApplicationOverview]
      (ok (get-open-reviews (getx-user-id))))

    (GET "/handled" []
      :summary "Lists applications which the user has already reviewed"
      :roles #{:handler :commenter :decider :past-commenter :past-decider}
      :return [ApplicationOverview]
      (ok (get-handled-reviews (getx-user-id))))))
