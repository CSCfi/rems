(ns rems.workflow.permissions
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]))

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
              (update application :permissions/by-role dissoc subject)
              (assoc-in application [:permissions/by-role subject] (set permissions))))
          application
          permission-map))

(deftest test-set-role-permissions
  (testing "adding"
    (is (= {:permissions/by-role {:role #{:foo :bar}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})))))
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
               (set-role-permissions {:role nil})))))

  (testing "can set permissions for multiple roles"
    (is (= {:permissions/by-role {:role-1 #{:foo}
                                  :role-2 #{:bar}}}
           (-> {}
               (set-role-permissions {:role-1 [:foo]
                                      :role-2 [:bar]})))))
  (testing "does not alter unrelated roles"
    (is (= {:permissions/by-role {:unrelated #{:foo}
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
                       :permissions/by-role {:applicant #{:foo}
                                             :handler #{:bar}}}]
      (is (= #{:foo :bar}
             (user-permissions application "user")))
      (is (= nil
             (user-permissions application "wrong user"))))))
