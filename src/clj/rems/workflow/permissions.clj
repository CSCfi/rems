(ns rems.workflow.permissions
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]))

(def ^:private conj-set (fnil conj (hash-set)))

(defn add-user-role [application user role]
  (assert (string? user))
  (assert (keyword? role))
  (update-in application [::user-roles user] conj-set role))

(defn- dissoc-if-empty [m k]
  (if (empty? (get m k))
    (dissoc m k)
    m))

(defn remove-user-role [application user role]
  (-> application
      (update-in [::user-roles user] disj role)
      (update ::user-roles dissoc-if-empty user)))

(defn user-roles [application user]
  (set (get-in application [::user-roles user])))

(deftest test-user-roles
  (testing "add first"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (add-user-role "user" :role-1)))))
  (testing "add more"
    (is (= {::user-roles {"user" #{:role-1 :role-2}}}
           (-> {}
               (add-user-role "user" :role-1)
               (add-user-role "user" :role-2)))))
  (testing "remove some"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (add-user-role "user" :role-1)
               (add-user-role "user" :role-2)
               (remove-user-role "user" :role-2)))))
  (testing "remove all"
    (is (= {::user-roles {}}
           (-> {}
               (add-user-role "user" :role-1)
               (remove-user-role "user" :role-1)))))
  (testing "multiple users, get the roles of a single user"
    (let [app (-> {}
                  (add-user-role "user-1" :role-1)
                  (add-user-role "user-2" :role-2))]
      (is (= #{:role-1} (user-roles app "user-1")))
      (is (= #{:role-2} (user-roles app "user-2")))
      (is (= #{} (user-roles app "user-3"))))))

(defn set-role-permissions
  "Sets role specific permissions for the application.

   In `permission-map`, the key is the role (a keyword), and the value
   is a list of permissions to set for that subject (also keywords).
   The permissions may represent commands that the user is allowed to run,
   or they may be used to specify whether the user can see all events and
   comments from the reviewers (e.g. `:see-everything`).

   There is a difference between an empty list of permissions and nil.
   Empty list means that the user has read-only access to the application,
   whereas nil means that the user has no access to the application.

   Users will be mapped to roles based on application state.
   The supported roles are defined in `user-permissions`."
  [application permission-map]
  (assert (every? keyword? (keys permission-map)))
  (reduce (fn [application [subject permissions]]
            (if (nil? permissions)
              (update application ::role-permissions dissoc subject)
              (assoc-in application [::role-permissions subject] (set permissions))))
          application
          permission-map))

(deftest test-set-role-permissions
  (testing "adding"
    (is (= {::role-permissions {:role #{:foo :bar}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})))))
  (testing "updating"
    (is (= {::role-permissions {:role #{:gazonk}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role [:gazonk]})))))
  (testing "removing (read-only access)"
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role []})))))
  (testing "removing (no access)"
    (is (= {::role-permissions {}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role nil})))))

  (testing "can set permissions for multiple roles"
    (is (= {::role-permissions {:role-1 #{:foo}
                                :role-2 #{:bar}}}
           (-> {}
               (set-role-permissions {:role-1 [:foo]
                                      :role-2 [:bar]})))))
  (testing "does not alter unrelated roles"
    (is (= {::role-permissions {:unrelated #{:foo}
                                :role #{:gazonk}}}
           (-> {}
               (set-role-permissions {:unrelated [:foo]
                                      :role [:bar]})
               (set-role-permissions {:role [:gazonk]}))))))

(defn user-permissions
  "Returns the specified user's permissions to this application.
   Union of all role specific permissions. Read the source
   to find out the supported roles."
  [application user-id]
  (let [applicant? (= user-id (:application/applicant application))
        handler? (contains? (:workflow.dynamic/handlers application) user-id)
        permissions (remove nil?
                            [(when applicant?
                               (get-in application [::role-permissions :applicant]))
                             (when handler?
                               (get-in application [::role-permissions :handler]))])]
    (if (empty? permissions)
      nil
      (apply set/union permissions))))

(deftest test-user-permissions
  (testing "no access"
    (is (= nil
           (user-permissions {}
                             "user"))))
  (testing "applicant permissions"
    (is (= #{:foo}
           (user-permissions {:application/applicant "user"
                              ::role-permissions {:applicant #{:foo}}}
                             "user"))))
  (testing "handler permissions"
    (is (= #{:foo}
           (user-permissions {:workflow.dynamic/handlers #{"user"}
                              ::role-permissions {:handler #{:foo}}}
                             "user"))))
  (testing "combined permissions from multiple roles"
    (let [application {:application/applicant "user"
                       :workflow.dynamic/handlers #{"user"}
                       ::role-permissions {:applicant #{:foo}
                                           :handler #{:bar}}}]
      (is (= #{:foo :bar}
             (user-permissions application "user")))
      (is (= nil
             (user-permissions application "wrong user"))))))

(defn cleanup [application]
  (dissoc application ::user-roles ::role-permissions))
