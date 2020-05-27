(ns rems.migrations.localize-organizations
  (:require [clojure.test :refer [deftest is]]
            [hugsql.core :as hugsql]
            [rems.json :as json]
            [rems.db.core :as db]))

(hugsql/def-db-fns-from-string
  "
-- :name get-organizations :? :*
SELECT
  id,
  data::TEXT
FROM organization

-- :name set-organization-data! :!
UPDATE organization SET data = :data::jsonb WHERE id = :id;
")

(defn- migrate-organization-name [organization]
  (let [organization-name (get-in organization [:data :organization/name])]
    (if (map? organization-name)
      organization
      (assoc-in organization [:data :organization/name] {:en organization-name}))))

(defn- migrate-organization-review-email [email]
  (let [email-name (:name email)]
    (if (map? email-name)
      email
      (assoc email :name {:en email-name}))))

(defn- migrate-organization-review-emails [organization]
  (update-in organization [:data :organization/review-emails] (partial map migrate-organization-review-email)))

(defn- migrate-organization [organization]
  (-> organization
      migrate-organization-name
      migrate-organization-review-emails))

(deftest test-migrate-organization
  (is (= {:id "csc" :data {:something 42
                           :organization/name {:en "CSC"}
                           :organization/review-emails [{:name {:en "CSC Office"} :email "csc-office@csc.fi"}]}}
         (migrate-organization {:id "csc" :data {:something 42
                                                 :organization/name "CSC"
                                                 :organization/review-emails [{:name "CSC Office" :email "csc-office@csc.fi"}]}}))))

(defn migrate-organizations! [conn organization]
  (doseq [{:keys [data] :as organization} organization
          :let [data (json/parse-string data)
                organization (assoc organization :data data)]
          :when (not (:organizations data))]
    (set-organization-data! conn
                            (update (migrate-organization organization)
                                    :data
                                    json/generate-string))))

(defn migrate-up [{:keys [conn]}]
  (migrate-organizations! conn (get-organizations conn)))

(comment
  (migrate-up {:conn rems.db.core/*db*}))
