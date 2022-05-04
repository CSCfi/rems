(ns rems.migrations.multiple-organizations
  (:require [clojure.test :refer [deftest is]]
            [hugsql.core :as hugsql]
            [rems.json :as json]
            [rems.db.core :as db]))

(hugsql/def-db-fns-from-string
  "
-- :name get-users :? :*
SELECT
  userId,
  userAttrs::TEXT
FROM users

-- :name set-user-attributes! :!
UPDATE users SET userAttrs = :userattrs::jsonb WHERE userid = :userid;
")

(defn- migrate-user-organizations [{:keys [userid userattrs]}]
  (let [existing-organizations (if (:organization userattrs)
                                 [{:organization/id (:organization userattrs)}]
                                 [])]
    {:userid userid
     :userattrs (-> userattrs
                    (assoc :organizations existing-organizations)
                    (dissoc :organization))}))

(deftest test-migrate-user-organizations
  (is (= {:userid "alice" :userattrs {:something 42
                                      :organizations []}}
         (migrate-user-organizations {:userid "alice"
                                      :userattrs {:something 42}}))
      "adds organizations even if empty")
  (is (= {:userid "alice" :userattrs {:something 42
                                      :organizations [{:organization/id "default"}]}}
         (migrate-user-organizations {:userid "alice"
                                      :userattrs {:something 42
                                                  :organization "default"}}))))

(defn migrate-user-organizations! [conn users]
  (doseq [{:keys [userattrs] :as user} users
          :let [userattrs (json/parse-string userattrs)
                user (assoc user :userattrs userattrs)]
          :when (not (:organizations userattrs))]
    (set-user-attributes! conn
                          (update (migrate-user-organizations user)
                                  :userattrs
                                  json/generate-string))))

(defn migrate-up [{:keys [conn]}]
  (migrate-user-organizations! conn (get-users conn)))

(comment
  (migrate-up {:conn rems.db.core/*db*})
  (db/add-user! rems.db.core/*db*
                {:user "alice"
                 :userattrs (json/generate-string {:userid "alice"
                                                   :email "alice@example.com"
                                                   :name "Alice Applicant"
                                                   :organization "default"})}))
