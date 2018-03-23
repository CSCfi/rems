(ns rems.test.api
  "Shared code for API testing"
  (:require [cheshire.core :refer [generate-string parse-stream]]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(defn api-fixture [f]
  (mount/start
   #'rems.config/env
   #'rems.env/*db*
   #'rems.handler/app)
  ;; TODO: silence logging somehow?
  (db/assert-test-database!)
  (migrations/migrate ["reset"] (select-keys env [:database-url]))
  (test-data/create-test-data!)
  (f)
  (mount/stop))

(defn authenticate [request api-key user-id]
  (-> request
      (assoc-in [:headers "x-rems-api-key"] api-key)
      (assoc-in [:headers "x-rems-user-id"] user-id)))

(defn json [request m]
  (-> request
      (content-type "application/json")
      (body (generate-string m))))

(defn read-body [{body :body}]
  (cond
    (string? body) body
    true (parse-stream (clojure.java.io/reader body) true)))
