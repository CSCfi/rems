(ns rems.db.users
  (:require [medley.core :refer [filter-vals]]
            [rems.cache :as cache]
            [rems.common.util :refer [build-index]]
            [rems.db.core :as db]
            [rems.db.user-settings]
            [rems.json :as json]))

(defn- get-default-attributes [userid]
  {:userid userid :name nil :email nil})

(defn- get-raw-user-attributes [{:keys [userid]}]
  (merge (get-default-attributes userid)
         (some-> (db/get-user-attributes {:user userid})
                 :userattrs
                 json/parse-string)))

(def user-cache
  (cache/basic {:id ::user-cache
                :miss-fn (fn [userid]
                           (get-raw-user-attributes {:userid userid}))
                :reload-fn (fn []
                             (->> (db/get-users)
                                  (filter :userid)
                                  (build-index {:keys [:userid]
                                                :value-fn get-raw-user-attributes})))}))

(defn add-user!
  "Create or update a user given a userid and a map of user attributes."
  [userid userattrs]
  (assert userid)
  (assert userattrs)
  (db/add-user! {:user userid
                 :userattrs (json/generate-string (dissoc userattrs :userid))})
  (cache/miss! user-cache userid))

(defn edit-user!
  "Update a user given user attributes."
  [userid userattrs]
  (assert userid)
  (assert userattrs)
  (db/edit-user! {:user userid
                  :userattrs (json/generate-string (dissoc userattrs :userid))})
  (cache/miss! user-cache userid))

;; XXX: here due to rems.db.applications, but joining another db entity should happen in service level
(defn- get-user-settings [userid]
  (-> (rems.db.user-settings/get-user-settings userid)
      (->> (filter-vals some?))
      (select-keys [:notification-email])))

(defn get-users []
  (->> (cache/entries! user-cache)
       (mapv (fn [[userid attributes]]
               (merge attributes (get-user-settings userid))))))

(defn get-user [userid]
  (let [attributes (or (cache/lookup! user-cache userid)
                       (get-default-attributes userid))]
    (merge attributes (get-user-settings userid))))

(defn user-exists? [userid]
  (cache/has? user-cache userid))

(defn join-user [x]
  (when-let [userid (:userid x)]
    (get-user userid)))

(defn remove-user! [userid]
  (assert userid)
  (db/remove-user! {:user userid})
  (cache/evict! user-cache userid))
