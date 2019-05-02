(ns rems.api.reviews
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :as applications-v2]
            [rems.api.schema :refer :all]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]))

(def ^:private reviewer-roles
  #{:handler :commenter :decider :past-commenter :past-decider})

(defn- review? [application]
  (and (some reviewer-roles (:application/roles application))
       (not= :application.state/draft (:application/state application))))

(defn get-all-reviews [user-id]
  (->> (applications-v2/get-all-applications user-id)
       (filter review?)))

(defn- open-review? [application]
  (and (= :application.state/submitted (:application/state application))
       (some #{:application.command/approve
               :application.command/comment
               :application.command/decide}
             (:application/permissions application))))

(defn get-open-reviews [user-id]
  (->> (get-all-reviews user-id)
       (filter open-review?)))

(defn get-handled-reviews [user-id]
  (->> (get-all-reviews user-id)
       (remove open-review?)))

(def reviews-api
  (context "/reviews" []
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
