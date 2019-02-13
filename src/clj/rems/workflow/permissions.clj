(ns rems.workflow.permissions
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]))

(defn- set-permissions
  "Sets permissions for the application. Use `set-role-permissions` or
  `set-user-permissions` instead of calling this function directly.

   In `permission-map`, the key is the subject (user or role), and the value
   is a list of permissions to set for that subject.

   There is a difference between an empty list of permissions and nil.
   Empty list means that the user has read-only access to the application,
   whereas nil means that the user has no access to the application.

   The permissions must be keywords. They may represent commands that the user
   is allowed to run, or they may be used to specify whether the user can see
   all events and comments from the reviewers (e.g. `:see-everything`)."
  [application category permission-map]
  (reduce (fn [application [subject permissions]]
            (if (nil? permissions)
              (update application category dissoc subject)
              (assoc-in application [category subject] (set permissions))))
          application
          permission-map))

(defn set-role-permissions
  "Sets role specific permissions for the application.

   Users will be mapped to roles based on application state.
   The supported roles are defined in `user-permissions`.

   The keys in `permission-map` are the role names as keywords.
   See `set-permissions` for details on `permission-map`."
  [application permission-map]
  (assert (every? keyword? (keys permission-map)))
  (set-permissions application :permissions/by-role permission-map))

(defn set-user-permissions
  "Sets user specific permissions for the application.

   User specific permissions can be used e.g. to give specific users
   commenting access or to give non-applicant members read-only
   access to the application.

   The keys in `permission-map` are the user IDs as strings.
   See `set-permissions` for details on `permission-map`."
  [application permission-map]
  (assert (every? string? (keys permission-map)))
  (set-permissions application :permissions/by-user permission-map))

(deftest test-set-permissions
  (testing "role-specific permissions"
    (is (= {:permissions/by-role {:role #{:foo :bar}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]}))))
    (testing "updating"
      (is (= {:permissions/by-role {:role #{:gazonk}}}
             (-> {}
                 (set-role-permissions {:role [:foo :bar]})
                 (set-role-permissions {:role [:gazonk]})))))
    (testing "removing (read-only access)"
      (is (= {:permissions/by-role {:role #{}}}
             (-> {}
                 (set-role-permissions {:role [:foo :bar]})
                 (set-role-permissions {:role []})))))
    (testing "removing (no access)"
      (is (= {:permissions/by-role {}}
             (-> {}
                 (set-role-permissions {:role [:foo :bar]})
                 (set-role-permissions {:role nil}))))))

  (testing "user-specific permissions"
    (is (= {:permissions/by-user {"user" #{:foo :bar}}}
           (-> {}
               (set-user-permissions {"user" [:foo :bar]}))))
    (testing "updating"
      (is (= {:permissions/by-user {"user" #{:gazonk}}}
             (-> {}
                 (set-user-permissions {"user" [:foo :bar]})
                 (set-user-permissions {"user" [:gazonk]})))))
    (testing "removing (read-only access)"
      (is (= {:permissions/by-user {"user" #{}}}
             (-> {}
                 (set-user-permissions {"user" [:foo :bar]})
                 (set-user-permissions {"user" []})))))
    (testing "removing (no access)"
      (is (= {:permissions/by-user {}}
             (-> {}
                 (set-user-permissions {"user" [:foo :bar]})
                 (set-user-permissions {"user" nil}))))))

  (testing "can set permissions for multiple roles/users"
    (is (= {:permissions/by-role {:role-1 #{:foo}
                                  :role-2 #{:bar}}}
           (-> {}
               (set-role-permissions {:role-1 [:foo]
                                      :role-2 [:bar]})))))
  (testing "does not alter unrelated roles/users"
    (is (= {:permissions/by-role {:unrelated #{:foo}
                                  :role #{:gazonk}}}
           (-> {}
               (set-role-permissions {:unrelated [:foo]
                                      :role [:bar]})
               (set-role-permissions {:role [:gazonk]}))))))

(defn user-permissions
  "Returns the specified user's permissions to this application.
   Union of all role and user specific permissions. Read the source
   to find out the supported roles. See also `set-permissions`."
  [application user-id]
  (let [applicant? (= user-id (:application/applicant application))
        handler? (contains? (:workflow.dynamic/handlers application) user-id)
        permissions (remove nil?
                            [(get-in application [:permissions/by-user user-id])
                             (when applicant?
                               (get-in application [:permissions/by-role :applicant]))
                             (when handler?
                               (get-in application [:permissions/by-role :handler]))])]
    (if (empty? permissions)
      nil
      (apply set/union permissions))))

(deftest test-user-permissions
  (testing "no access"
    (is (= nil
           (user-permissions {}
                             "user"))))
  (testing "read-only access"
    (is (= #{}
           (user-permissions {:permissions/by-user {"user" #{}}}
                             "user"))))
  (testing "user permissions"
    (is (= #{:foo}
           (user-permissions {:permissions/by-user {"user" #{:foo}}}
                             "user"))))
  (testing "applicant permissions"
    (is (= #{:foo}
           (user-permissions {:application/applicant "user"
                              :permissions/by-role {:applicant #{:foo}}}
                             "user"))))
  (testing "handler permissions"
    (is (= #{:foo}
           (user-permissions {:workflow.dynamic/handlers #{"user"}
                              :permissions/by-role {:handler #{:foo}}}
                             "user"))))
  (testing "combined permissions from multiple roles"
    (let [application {:application/applicant "user"
                       :workflow.dynamic/handlers #{"user"}
                       :permissions/by-user {"user" #{:foo}}
                       :permissions/by-role {:applicant #{:bar}
                                             :handler #{:gazonk}}}]
      (is (= #{:foo :bar :gazonk}
             (user-permissions application "user")))
      (is (= nil
             (user-permissions application "wrong user"))))))
