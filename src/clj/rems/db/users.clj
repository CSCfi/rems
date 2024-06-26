(ns rems.db.users
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [medley.core :refer [dissoc-in]]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.user-settings :as user-settings]
            [rems.json :as json]))

;; TODO: remove format/unformat, there should be no need
;; - attributes are the same in db
;; - attributes are not secret
;; - only the ones defined in config are originally saved
(defn format-user [u]
  (merge {:userid (:userid u) ; NB: currently we expect to return the keys always
          :name (:name u)
          :email (:email u)}
         (select-keys u [:organizations :notification-email :researcher-status-by])
         (select-keys u (map (comp keyword :attribute) (:oidc-extra-attributes env)))))

(defn- unformat-user
  "Inverse of format-user: take in API-style attributes and output db-style attributes"
  [u]
  (merge {:userid (:userid u) ; NB: currently we expect to return the keys always
          :name (:name u)
          :email (:email u)}
         (select-keys u [:organizations :researcher-status-by])
         (select-keys u (map (comp keyword :attribute) (:oidc-extra-attributes env)))))

(deftest test-format-unformat
  (is (= {:userid nil :name nil :email nil} (format-user nil))
      "current, perhaps nonsenical expectation")
  (is (= {:userid nil :name nil :email nil} (unformat-user nil))
      "current, perhaps nonsenical expectation")
  (let [api-user {:userid "foo" :name "bar" :email "a@b"}
        db-user {:userid "foo" :name "bar" :email "a@b"}]
    (is (= api-user (format-user (assoc db-user :extra-stuff "should-not-see"))))
    (is (= db-user (unformat-user (assoc api-user :extra-stuff "should-not-see"))))
    (is (= api-user (format-user (unformat-user api-user)))))
  (let [api-user {:userid "foo" :name "bar" :email "a@b" :organizations [{:organization/id "org1"} {:organization/id "org2"}]}
        db-user {:userid "foo" :name "bar" :email "a@b" :organizations [{:organization/id "org1"} {:organization/id "org2"}]}]
    (is (= api-user (format-user db-user)))
    (is (= db-user (unformat-user api-user)))
    (is (= api-user (format-user (unformat-user api-user))))))

(defn- invalid-user? [user]
  (or (str/blank? (:userid user))
      (str/blank? (:name user))))

(defn get-raw-user-attributes
  "Takes as user id as an input and fetches user attributes that are stored in a json blob in the users table.

   You should use get-user for most uses instead. It normalizes keys in the json blob."
  [userid]
  (some-> (db/get-user-attributes {:user userid})
          :userattrs
          json/parse-string))

;; XXX: should we really have this? we could get the settings where they are needed or to have a combined user map with everything?
(defn- get-user-attributes-from-settings [userid]
  (when-let [notification-email (:notification-email (user-settings/get-user-settings userid))]
    {:notification-email notification-email}))

(defn- merge-user-settings [user-attributes]
  (when (and user-attributes (:userid user-attributes))
    (merge user-attributes
           (get-user-attributes-from-settings (:userid user-attributes)))))

(defn- get-all-users-raw []
  (->> (db/get-users {})
       (map :userid)
       (map get-raw-user-attributes)
       (map merge-user-settings)
       doall))

(def user-cache (atom ::uninitialized))

(defn empty-user-cache! []
  (log/info "Emptying low level user cache")
  (reset! user-cache {:users-by-id-cache {}
                      :user-raw-attributes-by-id-cache {}}))

(defn reset-user-cache! []
  (log/info "Resetting low level user cache")
  (reset! user-cache ::uninitialized))

(defn reload-user-cache! []
  (log/info "Reloading low level user cache")
  (let [new-user-by-id-cache (atom (sorted-map))
        new-user-raw-attributes-by-id-cache (atom (sorted-map))]

    (doseq [user-attributes (db/get-user-attributes {})
            :let [fixed-user-attributes (some-> user-attributes
                                                :userattrs
                                                json/parse-string)
                  id (:userid user-attributes)]]
      (log/debug "Loading uncached user-attributes:" id)
      (swap! new-user-raw-attributes-by-id-cache assoc id fixed-user-attributes))

    (doseq [user (->> @new-user-raw-attributes-by-id-cache
                      vals
                      (map merge-user-settings)
                      (mapv format-user))
            :let [id (:userid user)]]
      (log/debug "Loading uncached user:" id)
      (swap! new-user-by-id-cache assoc id user))

    (reset! user-cache {:user-by-id-cache @new-user-by-id-cache
                        :user-raw-attributes-by-id-cache @new-user-raw-attributes-by-id-cache})

    (log/info "Reloaded low level user cache with" (count @new-user-by-id-cache) "users")
    (log/info "Reloaded low level user-attributes cache with" (count @new-user-raw-attributes-by-id-cache) "user-attributes")))

(mount/defstate low-level-user-cache
  :start (reload-user-cache!)
  :stop (reset-user-cache!))

(defn ensure-cache-is-initialized! []
  (assert (not= ::uninitialized @user-cache)))

(defn add-user-raw!
  "Create or update a user given a userid and a map of raw user attributes."
  [userid userattrs]
  (assert userid)
  (assert userattrs)
  (ensure-cache-is-initialized!)
  (db/add-user! {:user userid :userattrs (json/generate-string userattrs)})
  (swap! user-cache (fn [user-cache]
                      (-> user-cache
                          (assoc-in [:user-by-id-cache userid] (-> userattrs merge-user-settings format-user))
                          (assoc-in [:user-raw-attributes-by-id-cache userid] userattrs)))))

(defn add-user!
  "Create or update a user given form4atted user attributes."
  [user]
  (add-user-raw! (:userid user) (unformat-user user)))

(defn edit-user!
  "Update a user given formatted user attributes."
  [user]
  (ensure-cache-is-initialized!)
  (let [userid (:userid user)
        userattrs (unformat-user user)]
    (db/edit-user! {:user userid :userattrs (json/generate-string userattrs)})
    (swap! user-cache
           (fn [user-cache]
             (-> user-cache
                 (assoc-in [:user-by-id-cache userid] (-> user unformat-user merge-user-settings format-user))
                 (assoc-in [:user-raw-attributes-by-id-cache userid] userattrs))))))

(defn user-exists? [userid]
  (ensure-cache-is-initialized!)
  ;; NB: we can choose any cache here
  (contains? (:user-raw-attributes-by-id-cache @user-cache) userid))

(defn get-all-users []
  (ensure-cache-is-initialized!)
  (->> @user-cache
       :user-by-id-cache
       vals))

;; XXX: rename to `get-valid-users`?
(defn get-users []
  (->> (get-all-users)
       (remove invalid-user?)))

(def get-applicants get-users)
(def get-reviewers get-users)
(def get-deciders get-users)

;; NB: this is the roles table
(defn get-users-with-role [role]
  (->> (db/get-users-with-role {:role (name role)})
       (mapv :userid)))

(defn get-user
  "Given a userid, returns a map with keys :userid, :email, :name and optionally :notification-email"
  [userid]
  (ensure-cache-is-initialized!)
  (-> @user-cache
      :user-by-id-cache
      (get userid)
      ;; in case user attributes were not found, return at least the userid
      (assoc :userid userid)))

(defn join-user [x]
  (some-> x :userid get-user))

(defn remove-user! [userid]
  (assert userid)
  (ensure-cache-is-initialized!)
  ;; XXX: the user attributes, settings, roles etc. should perhaps be removed too?
  (db/remove-user! {:user userid})
  (swap! user-cache
         (fn [user-cache]
           (-> user-cache
               (dissoc-in [:users-by-id-cache userid])
               (dissoc-in [:user-raw-attributes-by-id-cache userid])))))
