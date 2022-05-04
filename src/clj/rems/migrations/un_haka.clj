(ns rems.migrations.un-haka
  (:require [clojure.test :refer [deftest is]]
            [hugsql.core :as hugsql]
            [medley.core :refer [map-keys]]
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

(defn- migrate-user [{:keys [userid userattrs]} swaps]
  {:userid userid
   :userattrs (map-keys #(swaps % %) userattrs)})

(deftest test-migrate-user
  (is (= {:userid "alice"
          :userattrs {:newthing 42}}
         (migrate-user {:userid "alice"
                        :userattrs {:something 42}}
                       {:something :newthing})))
  (is (= {:userid "alice"
          :userattrs {:something 42
                      :newthing ["foo"]}}
         (migrate-user {:userid "alice"
                        :userattrs {:something 42
                                    :else ["foo"]}}
                       {:else :newthing}))))

(defn migrate-users! [conn swaps]
  (doseq [{:keys [userattrs] :as user} (get-users conn)
          :let [userattrs (json/parse-string userattrs)
                user (assoc user :userattrs userattrs)
                new-user (update (migrate-user user swaps)
                                 :userattrs
                                 json/generate-string)]]
    (set-user-attributes! conn new-user)))

(def attribute-swaps
  {:eppn :userid
   :mail :email
   :commonName :name
   :displayName :name})

(def reverse-attribute-swaps
  ;; NB: let's call just having displayName or commonName enough
  (into {} (map (comp vec reverse) attribute-swaps)))

(defn migrate-up [{:keys [conn]}]
  (migrate-users! conn attribute-swaps))

(defn migrate-down [{:keys [conn]}]
  (migrate-users! conn reverse-attribute-swaps))

(comment
  (migrate-user {:userid "malice",
                 :userattrs (json/parse-string "{\"name\": \"Malice Applicant\", \"email\": \"malice@example.com\", \"other\": \"Attribute Value\", \"twinOf\": \"alice\", \"userid\": \"malice\"}")}
                reverse-attribute-swaps)
  (filter (comp #{"oldish"} :userid) (get-users rems.db.core/*db*))
  (migrate-up {:conn rems.db.core/*db*})
  (db/add-user! rems.db.core/*db*
                {:user "oldish"
                 :userattrs (json/generate-string {:userid "oldish"
                                                   :mail "oldish@example.com"
                                                   :displayName "Oldish Applicant"
                                                   :commonName "Oldish Applicant"
                                                   :other "attribute"})}))
