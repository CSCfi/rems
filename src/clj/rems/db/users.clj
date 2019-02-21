(ns rems.db.users
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(defn add-user! [user userattrs]
  (assert (and userattrs user) "User or user attributes missing!")
  (db/add-user! {:user user :userattrs (json/generate-string userattrs)}))

(defn add-user-if-logged-in! [user userattrs]
  (when user
    (add-user! user userattrs)))

(defn get-user-attributes
  "Takes as user id as an input and fetches user attributes that are stored in a json blob in the users table.
   Returns a structure like this:
   {:eppn \"developer\"
    :email \"developer@e.mail\"
    :displayName \"deve\"
    :surname \"loper\"
    ...etc}"
  [userid]
  (json/parse-string (:userattrs (db/get-user-attributes {:user userid})) true))

(defn get-all-users
  []
  (->> (db/get-users)
       (map :userid)
       (map get-user-attributes)
       (doall)))
