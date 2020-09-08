(ns rems.permissions
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals map-kv-vals]]
            [rems.common.util :refer [conj-set]]))

(defn- give-role-to-user [application role user]
  (assert (keyword? role) {:role role})
  (assert (string? user) {:user user})
  (update-in application [:application/user-roles user] conj-set role))

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
      (update-in [:application/user-roles user] disj role)
      (update :application/user-roles dissoc-if-empty user)))

(defn user-roles [application user]
  (let [specific-roles (set (get-in application [:application/user-roles user]))]
    (if (seq specific-roles)
      specific-roles
      #{:everyone-else})))

(deftest test-user-roles
  (testing "give first role"
    (is (= {:application/user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")))))
  (testing "give more roles"
    (is (= {:application/user-roles {"user" #{:role-1 :role-2}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")))))
  (testing "remove some roles"
    (is (= {:application/user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")
               (remove-role-from-user :role-2 "user")))))
  (testing "remove all roles"
    (is (= {:application/user-roles {}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (remove-role-from-user :role-1 "user")))))
  (testing "give a role to multiple users"
    (is (= {:application/user-roles {"user-1" #{:role-1}
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
            (assoc-in application [:application/role-permissions role] (set permissions)))
          application
          permission-map))

(deftest test-update-role-permissions
  (testing "adding"
    (is (= {:application/role-permissions {:role #{:foo :bar}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})))))
  (testing "updating"
    (is (= {:application/role-permissions {:role #{:gazonk}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role [:gazonk]})))))
  (testing "removing"
    (is (= {:application/role-permissions {:role #{}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role []}))))
    (is (= {:application/role-permissions {:role #{}}}
           (-> {}
               (update-role-permissions {:role [:foo :bar]})
               (update-role-permissions {:role nil})))))

  (testing "can set permissions for multiple roles"
    (is (= {:application/role-permissions {:role-1 #{:foo}
                                           :role-2 #{:bar}}}
           (-> {}
               (update-role-permissions {:role-1 [:foo]
                                         :role-2 [:bar]})))))
  (testing "does not alter unrelated roles"
    (is (= {:application/role-permissions {:unrelated #{:foo}
                                           :role #{:gazonk}}}
           (-> {}
               (update-role-permissions {:unrelated [:foo]
                                         :role [:bar]})
               (update-role-permissions {:role [:gazonk]}))))))

(defn- validate-rule [{:keys [role permission] :as rule}]
  (assert (or (keyword? role)
              (nil? role))
          {:message "invalid role" :rule rule})
  (assert (keyword? permission)
          {:message "invalid permission" :rule rule}))

(defn compile-rules
  "Compiles rules of the format `[{:role keyword :permission keyword}]`
  to the format expected by the `whitelist` and `blacklist` functions.
  If `:role` is missing or nil, it means every role."
  [rules]
  (doseq [rule rules]
    (validate-rule rule))
  (->> rules
       (group-by :role)
       (map-vals (fn [rules]
                   (set (map :permission rules))))))

(defn- map-permissions [application f]
  (update application :application/role-permissions #(map-kv-vals f %)))

(defn- permissions-for-role [rules role]
  (set/union (get rules role #{})
             (get rules nil #{})))

(defn blacklist
  "Applies rules for restricting the possible permissions.
  `rules` is what `compile-rules` returns."
  [application rules]
  (map-permissions application (fn [role permissions]
                                 (set/difference permissions (permissions-for-role rules role)))))

(deftest test-blacklist
  (let [app (-> {}
                (update-role-permissions {:role-1 [:foo :bar]})
                (update-role-permissions {:role-2 [:foo :bar]}))]
    (testing "disallow a permission for all roles"
      (is (= {:application/role-permissions {:role-1 #{:bar}
                                             :role-2 #{:bar}}}
             (blacklist app (compile-rules [{:permission :foo}])))))
    (testing "disallow a permission for a single role"
      (is (= {:application/role-permissions {:role-1 #{:bar}
                                             :role-2 #{:foo :bar}}}
             (blacklist app (compile-rules [{:role :role-1 :permission :foo}])))))
    (testing "multiple rules"
      (is (= {:application/role-permissions {:role-1 #{:bar}
                                             :role-2 #{:foo}}}
             (blacklist app (compile-rules [{:role :role-1 :permission :foo}
                                            {:role :role-2 :permission :bar}])))))))

(defn whitelist
  "Applies rules for restricting the possible permissions.
  `rules` is what `compile-rules` returns."
  [application rules]
  (map-permissions application (fn [role permissions]
                                 (set/intersection permissions (permissions-for-role rules role)))))

(deftest test-whitelist
  (let [app (-> {}
                (update-role-permissions {:role-1 [:foo :bar]})
                (update-role-permissions {:role-2 [:foo :bar]}))]
    (testing "allow a permission for all roles"
      (is (= {:application/role-permissions {:role-1 #{:foo}
                                             :role-2 #{:foo}}}
             (whitelist app (compile-rules [{:permission :foo}])))))
    (testing "allow a permission for a single role"
      (is (= {:application/role-permissions {:role-1 #{:foo}
                                             :role-2 #{}}}
             (whitelist app (compile-rules [{:role :role-1 :permission :foo}])))))
    (testing "multiple rules"
      (is (= {:application/role-permissions {:role-1 #{:foo}
                                             :role-2 #{:bar}}}
             (whitelist app (compile-rules [{:role :role-1 :permission :foo}
                                            {:role :role-2 :permission :bar}])))))))

(defn user-permissions
  "Returns a set of the specified user's permissions to this application.
   Union of all role specific permissions. Returns an empty set if no
   permissions are set for the user."
  [application user]
  (->> (user-roles application user)
       (mapcat (fn [role]
                 (get-in application [:application/role-permissions role])))
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
  (dissoc application :application/user-roles :application/role-permissions))
