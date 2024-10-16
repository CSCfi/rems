(ns rems.service.users
  (:require [clojure.string :as str]
            [rems.db.roles]
            [rems.db.users]
            [rems.db.user-settings]))

(defn add-user! [user]
  (let [userattrs (dissoc user :notification-email)]
    (rems.db.users/add-user! (:userid user) userattrs)))

(defn edit-user! [user]
  (let [userattrs (dissoc user :notification-email)]
    (rems.db.users/edit-user! (:userid user) userattrs)))

(defn user-exists? [userid]
  (rems.db.users/user-exists? userid))

(defn- invalid-user? [user]
  (or (str/blank? (:userid user))
      (str/blank? (:name user))))

(defn get-user [userid]
  (let [user (rems.db.users/get-user userid)]
    (when-not (invalid-user? user)
      user)))

(defn get-users []
  (->> (rems.db.users/get-users)
       (into [] (remove invalid-user?))))
