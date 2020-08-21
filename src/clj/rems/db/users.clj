(ns rems.db.users
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.user-settings :as user-settings]
            [rems.json :as json]))

(defn format-user [u]
  (merge {:userid (:eppn u)
          :name (or (:commonName u)
                    (:displayName u)) ;; some shibboleth idps don't send commonName
          :email (:mail u)}
         (select-keys u [:organizations :notification-email])
         (select-keys u (map (comp keyword :attribute) (:oidc-extra-attributes env)))))

(defn- unformat-user
  "Inverse of format-user: take in API-style attributes and output db-style attributes"
  [u]
  (merge {:eppn (:userid u)
          :commonName (:name u)
          :mail (:email u)}
         (select-keys u [:organizations])
         (select-keys u (map (comp keyword :attribute) (:oidc-extra-attributes env)))))

(deftest test-format-unformat
  (let [api-user {:userid "foo" :name "bar" :email "a@b"}
        db-user {:eppn "foo" :commonName "bar" :mail "a@b"}]
    (is (= api-user (format-user db-user)))
    (is (= db-user (unformat-user api-user)))
    (is (= api-user (format-user (unformat-user api-user)))))
  (let [api-user {:userid "foo" :name "bar" :email "a@b" :organizations [{:organization/id "org1"} {:organization/id "org2"}]}
        db-user {:eppn "foo" :commonName "bar" :mail "a@b" :organizations [{:organization/id "org1"} {:organization/id "org2"}]}]
    (is (= api-user (format-user db-user)))
    (is (= db-user (unformat-user api-user)))
    (is (= api-user (format-user (unformat-user api-user))))))

(defn- invalid-user? [u]
  (let [user (format-user u)]
    (or (str/blank? (:userid user))
        (str/blank? (:name user)))))

(defn add-user-raw!
  "Create or update a user given a userid and a map of raw user attributes."
  [userid userattrs]
  (assert userid)
  (assert userattrs)
  (db/add-user! {:user userid :userattrs (json/generate-string userattrs)}))

(defn add-user!
  "Create or update a user given formatted user attributes."
  [user]
  (add-user-raw! (:userid user) (unformat-user user)))

(defn get-raw-user-attributes
  "Takes as user id as an input and fetches user attributes that are stored in a json blob in the users table.

   You should use get-user for most uses instead. It normalizes keys in the json blob."
  [userid]
  (when-let [json (:userattrs (db/get-user-attributes {:user userid}))]
    (json/parse-string json)))

(defn- user-attributes-from-settings [userid]
  (when-let [notification-email (:notification-email (user-settings/get-user-settings userid))]
    {:notification-email notification-email}))

(defn- merge-user-settings [raw-attributes]
  (merge raw-attributes
         (user-attributes-from-settings (:eppn raw-attributes))))

(defn user-exists? [userid]
  (some? (get-raw-user-attributes userid)))

(defn- get-all-users-raw []
  (->> (db/get-users)
       (map :userid)
       (map get-raw-user-attributes)
       (map merge-user-settings)
       (doall)))

(defn get-all-users []
  (map format-user (get-all-users-raw)))

;; TODO Filter applicant, requesting user
;;
;; XXX: Removing invalid users is not done consistently. It seems that
;;   only the following API calls are affected:
;;
;;     /applications/reviewers
;;     /applications/members
;;     /applications/deciders
;;     /workflows/actors
;;
;;   For example, a user without commonName is able to log in and send an
;;   application, and the application is visible to the handler and can
;;   be approved.
(defn get-users []
  (->> (get-all-users-raw)
       (remove invalid-user?)
       (map format-user)))

(def get-applicants get-users)

(def get-reviewers get-users)

(def get-deciders get-users)

(defn get-users-with-role [role]
  (->> (db/get-users-with-role {:role (name role)})
       (map :userid)
       (doall)))

(defn get-user
  "Given a userid, returns a map with keys :userid, :email, :name and optionally :notification-email"
  [userid]
  (-> userid
      get-raw-user-attributes
      merge-user-settings
      format-user
      ;; in case user attributes were not found, return at least the userid
      (assoc :userid userid)))
