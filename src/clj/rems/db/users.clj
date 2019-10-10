(ns rems.db.users
  (:require [clojure.string :as str]
            [rems.db.core :as db]
            [rems.json :as json]))

;; TODO could pass through additional (configurable?) attributes
(defn format-user [u]
  {:userid (:eppn u)
   :name (:commonName u)
   :email (:mail u)})

(defn- invalid-user? [u]
  (or (str/blank? (:eppn u))
      (str/blank? (:commonName u))
      (str/blank? (:mail u))))

(defn add-user! [user userattrs]
  (assert (and userattrs user) "User or user attributes missing!")
  (db/add-user! {:user user :userattrs (json/generate-string userattrs)}))

(defn add-user-if-logged-in! [user userattrs]
  (when user
    (add-user! user userattrs)))

(defn- get-user-attributes
  "Takes as user id as an input and fetches user attributes that are stored in a json blob in the users table.
   Returns a structure like this:
   {:eppn \"developer\"
    :email \"developer@e.mail\"
    :displayName \"deve\"
    :surname \"loper\"
    ...etc}

  You should use get-user instead."
  [userid]
  (json/parse-string (:userattrs (db/get-user-attributes {:user userid}))))

(defn- get-all-users []
  (->> (db/get-users)
       (map :userid)
       (map get-user-attributes)
       (doall)))

;; TODO Filter applicant, requesting user
;;
;; XXX: Removing invalid users is not done consistently. It seems that
;;   only the following API calls are affected:
;;
;;     /applications/commenters
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

(def get-commenters get-users)

(def get-deciders get-users)

(defn get-users-with-role [role]
  (->> (db/get-users-with-role {:role (name role)})
       (map :userid)
       (doall)))

(defn get-user
  "Given a userid, returns a map with keys :userid, :email and :name."
  [userid]
  (-> userid
      get-user-attributes
      format-user
      ;; hack for users without attributes
      (assoc :userid userid)))
