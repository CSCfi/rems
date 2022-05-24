(ns rems.migrations.slim-owners
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

(defn- migrate-organization-owner-up [owner]
  (select-keys owner [:userid]))

(defn- migrate-organization-owners [organization migrate-fn]
  (update-in organization [:data :organization/owners] (partial map #(migrate-fn %))))

(deftest test-migrate-organization
  (is (= {:id "csc"
          :data {:something 42
                 :organization/name {:fi "CSC"
                                     :en "CSC"}
                 :organization/short-name {:fi "CSC"
                                           :en "CSC"}
                 :organization/owners [{:userid "alice"}
                                       {:userid "bob"}]
                 :organization/review-emails [{:name {:fi "CSC Office"
                                                      :en "CSC Office"}
                                               :email "csc-office@csc.fi"}]}}
         (migrate-organization-owners {:id "csc"
                                       :data {:something 42
                                              :organization/name {:fi "CSC"
                                                                  :en "CSC"}
                                              :organization/short-name {:fi "CSC"
                                                                        :en "CSC"}
                                              :organization/owners [{:userid "alice" :name "Alice Applicant"}
                                                                    {:userid "bob" :name "Bob Bannister" :email "bob@legal.gov"}]
                                              :organization/review-emails [{:name {:fi "CSC Office"
                                                                                   :en "CSC Office"}
                                                                            :email "csc-office@csc.fi"}]}}
                                      migrate-organization-owner-up))))

(defn migrate-organizations! [conn organization migrate-fn]
  (doseq [{:keys [data] :as organization} organization
          :let [data (json/parse-string data)
                organization (assoc organization :data data)]
          :when (not (:organizations data))]
    (set-organization-data! conn
                            (update (migrate-organization-owners organization migrate-fn)
                                    :data
                                    json/generate-string))))

(defn migrate-up [{:keys [conn]}]
  (migrate-organizations! conn (get-organizations conn) migrate-organization-owner-up))

(comment
  ;; creating this organization will preveng logging in
  (rems.db.organizations/add-organization! {:organization/id "test"
                                            :organization/name {:fi "Test"
                                                                :en "Test"}
                                            :organization/short-name {:fi "Test"
                                                                      :en "Test"}
                                            :organization/owners [{:userid "alice" :name "Alice Applicant"} ; NB: extra
                                                                  {:userid "bob" :name "Bob Bannister" :email "bob@legal.gov"}]
                                            :organization/review-emails [{:name {:fi "Test"
                                                                                 :en "Test"}
                                                                          :email "test@example.org"}]})
  ;; but migration fixes it!
  (migrate-up {:conn rems.db.core/*db*}))
