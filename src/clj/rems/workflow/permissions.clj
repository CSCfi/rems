(ns rems.workflow.permissions
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]))

(def ^:private conj-set (fnil conj (hash-set)))

;; TODO: would give-role or give-role-to-user be a better name?
(defn add-user-role [application user role]
  (assert (string? user)
          (str "user must be a string: " (pr-str user)))
  (assert (keyword? role)
          (str "role must be a keyword: " (pr-str role)))
  (update-in application [::user-roles user] conj-set role))

;; TODO: merge with add-user-role?
(defn add-users-role [application users role]
  (reduce (fn [app user]
            (add-user-role app user role))
          application
          users))

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
  (testing "add first role"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (add-user-role "user" :role-1)))))
  (testing "add more roles"
    (is (= {::user-roles {"user" #{:role-1 :role-2}}}
           (-> {}
               (add-user-role "user" :role-1)
               (add-user-role "user" :role-2)))))
  (testing "remove some roles"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (add-user-role "user" :role-1)
               (add-user-role "user" :role-2)
               (remove-user-role "user" :role-2)))))
  (testing "remove all roles"
    (is (= {::user-roles {}}
           (-> {}
               (add-user-role "user" :role-1)
               (remove-user-role "user" :role-1)))))
  (testing "give a role to multiple users"
    (is (= {::user-roles {"user-1" #{:role-1}
                          "user-2" #{:role-1}}}
           (-> {}
               (add-users-role ["user-1" "user-2"] :role-1)))))
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
   is a list of permissions to set for that role (also keywords).
   The permissions may represent commands that the user is allowed to run,
   or they may be used to specify whether the user can see all events and
   comments from the reviewers (e.g. `:see-everything`)."
  [application permission-map]
  (reduce (fn [application [role permissions]]
            (assert (keyword? role))
            (assoc-in application [::role-permissions role] (set permissions)))
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
  (testing "removing"
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role []}))))
    (is (= {::role-permissions {:role #{}}}
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
   Union of all role specific permissions."
  [application user]
  (->> (user-roles application user)
       (map (fn [role]
              (set (get-in application [::role-permissions role]))))
       (apply set/union)))

(deftest test-user-permissions
  (testing "unknown user"
    (is (= #{}
           (user-permissions {} "user"))))
  (testing "one role"
    (is (= #{:foo}
           (-> {}
               (add-user-role "user" :role-1)
               (set-role-permissions {:role-1 #{:foo}})
               (user-permissions "user")))))
  (testing "multiple roles"
    (is (= #{:foo :bar}
           (-> {}
               (add-user-role "user" :role-1)
               (add-user-role "user" :role-2)
               (set-role-permissions {:role-1 #{:foo}
                                      :role-2 #{:bar}})
               (user-permissions "user"))))))

(defn cleanup [application]
  (dissoc application ::user-roles ::role-permissions))
