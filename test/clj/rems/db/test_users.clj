(ns ^:integration rems.db.test-users
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users :as users]
            [rems.db.user-settings :as user-settings]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-users
  ;; TODO: enforce that userid must be same?
  (users/add-user-raw! "user1" {:userid "whatever"
                                :name "What Ever"
                                :some-attr "some value"})
  (users/add-user-raw! "user-with-org" {:userid "user-with-org"
                                        :name "User Org"
                                        :email "user@org"
                                        ;;:notification-email "user@alt"
                                        :organizations [{:organization/id "org"}]})

  (testing "survives partial user settings"
    (db/update-user-settings! {:user "user1" :settings "{\"language\": \"fi\"}"}) ; missing notification-email

    (is (= {:userid "user1"
            :name "What Ever"
            :email nil}
           (users/get-user "user1")))

    (is (= {:language :fi :notification-email nil} (user-settings/get-user-settings "user1"))
        "default is returned for notification-email")

    (is (= {:success true}
           (user-settings/update-user-settings! "user1" {:language :en}))
        "settings can be updated")

    (is (= {:userid "user1"
            :name "What Ever"
            :email nil}
           (users/get-user "user1")))

    (is (= {:language :en :notification-email nil} (user-settings/get-user-settings "user1"))
        "default is returned for notification-email"))

  (testing "get-raw-user-attributes"
    (is (= {:userid "whatever"
            :name "What Ever"
            :some-attr "some value"}
           (#'users/get-raw-user-attributes "user1")))
    (is (= {:userid "user-with-org"
            :name "User Org"
            :email "user@org"
            :organizations [{:organization/id "org"}]}
           (#'users/get-raw-user-attributes "user-with-org"))))

  (testing "get-user"
    (is (= {:userid "user1"
            :name "What Ever"
            :email nil}
           (users/get-user "user1")))
    (is (= {:userid "user-with-org"
            :name "User Org"
            :email "user@org"
            :organizations [{:organization/id "org"}]}
           (users/get-user "user-with-org"))))

  (testing "get-all-users"
    (is (= [{:userid "user-with-org"
             :name "User Org"
             :email "user@org"
             :organizations [{:organization/id "org"}]}
            {:userid "whatever"
             :name "What Ever"
             :email nil}]
           (sort-by :userid (users/get-all-users)))))

  (testing "get-users-with-role"
    (roles/add-role! "user1" :owner)
    (is (= ["user1"] (users/get-users-with-role :owner)))
    (is (= [] (users/get-users-with-role :reporter))))

  (testing "get-deciders"
    (is (= #{{:userid "whatever", :name "What Ever", :email nil}
             {:userid "user-with-org",
              :name "User Org",
              :email "user@org",
              :organizations [#:organization{:id "org"}]}} (set (users/get-deciders)))))

  (testing "update user with add-user-raw!"
    (users/add-user-raw! "user1" {:userid "user1"
                                  :name "new name"})
    (is (= {:userid "user1"
            :name "new name"
            :email nil}
           (users/get-user "user1"))))

  (testing "update user with add-user!"
    (users/add-user! {:userid "user1"
                      :name "newer name"
                      :email "foo@example.com"})
    (is (= {:userid "user1"
            :name "newer name"
            :email "foo@example.com"}
           (users/get-user "user1")))))

(deftest test-nonexistent-user
  (is (= {:userid "nonexistent"
          :name nil
          :email nil}
         (users/get-user "nonexistent"))))
