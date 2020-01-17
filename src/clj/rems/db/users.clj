(ns rems.db.users
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.json :as json]))

;; TODO could pass through additional (configurable?) attributes
(defn format-user [u]
  {:userid (:eppn u)
   :name (or (:commonName u)
             (:displayName u)) ;; some shibboleth idps don't send commonName
   :email (:mail u)})

(defn unformat-user
  "Inverse of format-user: take in API-style attributes and output db-style attributes"
  [u]
  {:eppn (:userid u)
   :commonName (:name u)
   :mail (:email u)})

(deftest test-format-unformat
  (let [api-user {:userid "foo" :name "bar" :email "a@b"}
        db-user {:eppn "foo" :commonName "bar" :mail "a@b"}]
    (is (= api-user (format-user db-user)))
    (is (= db-user (unformat-user api-user)))
    (is (= api-user (format-user (unformat-user api-user))))))

(defn- invalid-user? [u]
  (let [user (format-user u)]
    (or (str/blank? (:userid user))
        (str/blank? (:name user)))))

(defn add-user! [user userattrs]
  (assert user)
  (assert userattrs)
  (db/add-user! {:user user :userattrs (json/generate-string userattrs)}))

(defn add-user-if-logged-in! [user userattrs]
  (when user
    (add-user! user userattrs)))

(defn get-raw-user-attributes
  "Takes as user id as an input and fetches user attributes that are stored in a json blob in the users table.

   You should use get-user for most uses instead. It normalizes keys in the json blob."
  [userid]
  (when-let [json (:userattrs (db/get-user-attributes {:user userid}))]
    (json/parse-string json)))

(defn user-exists? [userid]
  (some? (get-raw-user-attributes userid)))

(defn- get-all-users []
  (->> (db/get-users)
       (map :userid)
       (map get-raw-user-attributes)
       (doall)))

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
  (->> (get-all-users)
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
  "Given a userid, returns a map with keys :userid, :email and :name."
  [userid]
  (-> userid
      get-raw-user-attributes
      format-user
      ;; in case user attributes were not found, return at least the userid
      (assoc :userid userid)))
