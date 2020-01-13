(ns rems.api.health
  (:require [compojure.api.sweet :refer :all]
            [rems.db.events :as events]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import org.joda.time.DateTime))

(s/defschema Health
  {:healthy s/Bool
   :latest-event (s/maybe DateTime)})

(defn- health []
  (let [latest (events/get-latest-event)]
    ;; unhealthy statuses are currently reported via exceptions, i.e. non-200 status codes
    {:healthy true
     :latest-event (:event/time latest)}))

(def health-api
  (context "/health" []
    :tags [:health]
    (GET "/" []
      :summary "Health check"
      :return Health
      (ok (health)))))
