(ns rems.permissions
  (:require [clojure.test :refer [deftest is testing]]
            [rems.util :refer [conj-set]]))

(defn- give-role-to-user [application role user]
  (assert (keyword? role) {:role role})
  (assert (string? user) {:user user})
  (update-in application [::user-roles user] conj-set role))

(defn give-role-to-users [application role users]
  (reduce (fn [app user]
            (give-role-to-user app role user))
          application
          users))

(defn- dissoc-if-empty [m k]
  (if (empty? (get m k))
    (dissoc m k)
    m))

(defn remove-role-from-user [application role user]
  (assert (keyword? role) {:role role})
  (assert (string? user) {:user user})
  (-> application
      (update-in [::user-roles user] disj role)
      (update ::user-roles dissoc-if-empty user)))

(defn user-roles [application user]
  (let [specific-roles (set (get-in application [::user-roles user]))]
    (if (seq specific-roles)
      specific-roles
      #{:everyone-else})))

(deftest test-user-roles
  (testing "give first role"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")))))
  (testing "give more roles"
    (is (= {::user-roles {"user" #{:role-1 :role-2}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")))))
  (testing "remove some roles"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")
               (remove-role-from-user :role-2 "user")))))
  (testing "remove all roles"
    (is (= {::user-roles {}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (remove-role-from-user :role-1 "user")))))
  (testing "give a role to multiple users"
    (is (= {::user-roles {"user-1" #{:role-1}
                          "user-2" #{:role-1}}}
           (-> {}
               (give-role-to-users :role-1 ["user-1" "user-2"])))))
  (testing "multiple users, get the roles of a single user"
    (let [app (-> {}
                  (give-role-to-user :role-1 "user-1")
                  (give-role-to-user :role-2 "user-2"))]
      (is (= #{:role-1} (user-roles app "user-1")))
      (is (= #{:role-2} (user-roles app "user-2")))
      (is (= #{:everyone-else} (user-roles app "user-3"))))))

(defn update-role-permissions
  "Sets role specific permissions for the application.

   In `permission-map`, the key is the role (a keyword), and the value
   is a list of permissions to set for that role (also keywords).
   The permissions may represent commands that the user is allowed to run,
   or they may be used to specify whether the user can see all events and
   comments from the reviewers (e.g. `:see-everything`)."
  [application permission-map]
  (reduce (fn [application [role permissions]]
            (assert (keyword? role) {:role role})
            (assoc-in application [::role-permissions role] (set permissions)))
          application
          permission-map))

(deftest test-update-role-permissions
  (testing "adding"
    (is (= {::role-permissions {:role #{:foo :bar}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})))))
  (testing "updating"
    (is (= {::role-permissions {:role #{:gazonk}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role [:gazonk]})))))
  (testing "removing"
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role []}))))
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role nil})))))

  (testing "can set permissions for multiple roles"
    (is (= {::role-permissions {:role-1 #{:foo}
                                :role-2 #{:bar}}}
           (-> {}
               (update-role-permissions {:role-1 [:foo]
                                         :role-2 [:bar]})))))
  (testing "does not alter unrelated roles"
    (is (= {::role-permissions {:unrelated #{:foo}
                                :role #{:gazonk}}}
           (-> {}
               (update-role-permissions {:unrelated [:foo]
                                         :role [:bar]})
               (update-role-permissions {:role [:gazonk]}))))))

(defn- remove-permission-from-role [application role permission]
  (assert (keyword? role) {:role role})
  (update-in application [::role-permissions role] (fn [permissions]
                                                     (-> (set permissions)
                                                         (disj permission)))))

(defn- remove-permission-from-all-roles [application permission]
  (let [roles (keys (::role-permissions application))]
    (reduce (fn [application role]
              (update-in application [::role-permissions role] disj permission))
            application
            roles)))

(defn- remove-permission [application {:keys [role permission]}]
  (if (keyword? role)
    (remove-permission-from-role application role permission)
    (remove-permission-from-all-roles application permission)))

(defn restrict
  "Applies rules for restricting the possible permissions.
  `restrictions` should list the permissions to remove in the format
  `[{:role keyword :permission keyword}]` where `:role` is optional."
  [application restrictions]
  (reduce remove-permission application restrictions))

(deftest test-restrict
  (testing "restrict a permission for all roles"
    (is (= {:rems.permissions/role-permissions {:role-1 #{:bar}
                                                :role-2 #{}}}
           (-> {}
               (update-role-permissions {:role-1 [:foo :bar]})
               (update-role-permissions {:role-2 [:foo]})
               (restrict [{:permission :foo}])))))
  (testing "restrict a permission for a single role"
    (is (= {:rems.permissions/role-permissions {:role-1 #{:bar}
                                                :role-2 #{:foo}}}
           (-> {}
               (update-role-permissions {:role-1 [:foo :bar]})
               (update-role-permissions {:role-2 [:foo]})
               (restrict [{:role :role-1 :permission :foo}])))))
  (testing "multiple restrictions"
    (is (= {:rems.permissions/role-permissions {:role-1 #{:bar}
                                                :role-2 #{:foo}}}
           (-> {}
               (update-role-permissions {:role-1 [:foo :bar]})
               (update-role-permissions {:role-2 [:foo :bar]})
               (restrict [{:role :role-1 :permission :foo}
                          {:role :role-2 :permission :bar}]))))))

(defn user-permissions
  "Returns a set of the specified user's permissions to this application.
   Union of all role specific permissions. Returns an empty set if no
   permissions are set for the user."
  [application user]
  (->> (user-roles application user)
       (mapcat (fn [role]
                 (get-in application [::role-permissions role])))
       set))

(deftest test-user-permissions
  (testing "unknown user"
    (is (= #{}
           (user-permissions {} "user"))))
  (testing "one role"
    (is (= #{:foo}
           (-> {}
               (give-role-to-user :role-1 "user")
               (update-role-permissions {:role-1 #{:foo}})
               (user-permissions "user")))))
  (testing "multiple roles"
    (is (= #{:foo :bar}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")
               (update-role-permissions {:role-1 #{:foo}
                                         :role-2 #{:bar}})
               (user-permissions "user"))))))

(defn cleanup [application]
  (dissoc application ::user-roles ::role-permissions))
