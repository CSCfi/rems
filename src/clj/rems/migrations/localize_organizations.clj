(ns rems.migrations.localize-organizations
  (:require [clojure.test :refer [deftest is]]
            [hugsql.core :as hugsql]
            [rems.json :as json]
            [rems.config :refer [env]]
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

(defn- migrate-organization-name [organization languages]
  (let [organization-name (get-in organization [:data :organization/name])]
    (if (map? organization-name)
      organization
      (assoc-in organization [:data :organization/name] (into {} (for [language languages] [language organization-name]))))))

(defn- migrate-organization-short-name [organization languages]
  (let [organization-short-name (get-in organization [:data :organization/short-name])
        organization-name (get-in organization [:data :organization/name])]
    (if (map? organization-short-name)
      organization
      (assoc-in organization [:data :organization/short-name] organization-name)))) ; :name is migrated already

(defn- migrate-organization-review-email [email languages]
  (let [email-name (:name email)]
    (if (map? email-name)
      email
      (assoc email :name (into {} (for [language languages] [language email-name]))))))

(defn- migrate-organization-review-emails [organization languages]
  (update-in organization [:data :organization/review-emails] (partial map #(migrate-organization-review-email % languages))))

(defn- migrate-organization [organization languages]
  (-> organization
      (migrate-organization-name languages)
      (migrate-organization-short-name languages)
      (migrate-organization-review-emails languages)))

(deftest test-migrate-organization
  (is (= {:id "csc" :data {:something 42
                           :organization/name {:fi "CSC"
                                               :en "CSC"}
                           :organization/short-name {:fi "CSC"
                                                     :en "CSC"}
                           :organization/review-emails [{:name {:fi "CSC Office"
                                                                :en "CSC Office"}
                                                         :email "csc-office@csc.fi"}]}}
         (migrate-organization {:id "csc" :data {:something 42
                                                 :organization/name "CSC"
                                                 :organization/review-emails [{:name "CSC Office"
                                                                               :email "csc-office@csc.fi"}]}}
                               [:en :fi]))))

(defn migrate-organizations! [conn organization languages]
  (doseq [{:keys [data] :as organization} organization
          :let [data (json/parse-string data)
                organization (assoc organization :data data)]
          :when (not (:organizations data))]
    (set-organization-data! conn
                            (update (migrate-organization organization languages)
                                    :data
                                    json/generate-string))))

(defn migrate-up [{:keys [conn]}]
  (migrate-organizations! conn (get-organizations conn) (:languages env)))

(comment
  (migrate-up {:conn rems.db.core/*db*}))
