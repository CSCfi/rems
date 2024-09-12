(ns ^:integration rems.db.test-users
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.core :as db]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users]
            [rems.db.user-settings]
            [rems.json :as json]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-users
  (testing "get users"
    (rems.db.users/add-user! "user1" {:userid "whatever" ; userid is ignored
                                      :name "What Ever"
                                      :some-attr "some value"})
    (is (= {:userid "user1"
            :name "What Ever"
            :email nil
            :some-attr "some value"}
           (rems.db.users/get-user "user1")))

    (rems.db.users/add-user! "user-with-org" {:name "User Org"
                                              :email "user@org"
                                              :organizations [{:organization/id "org"}]})
    (is (= {:userid "user-with-org"
            :name "User Org"
            :email "user@org"
            :organizations [{:organization/id "org"}]}
           (rems.db.users/get-user "user-with-org")))

    (testing "user has different userid in userattrs"
      ;; NB: raw db call due testing backwards compatible behavior
      (db/add-user! {:user "different-userid" :userattrs (json/generate-string {:userid "bad"})})
      (cache/reset! rems.db.users/user-cache)
      (is (= {:userid "bad"
              :name nil
              :email nil}
             (rems.db.users/get-user "different-userid"))))

    (testing "non-existent user gets default attributes but is not persisted"
      (is (= {:userid "nonexistent"
              :name nil
              :email nil}
             (rems.db.users/get-user "nonexistent")))

      (is (= [{:userid "bad" :name nil :email nil}
              {:userid "user-with-org" :name "User Org" :email "user@org" :organizations [{:organization/id "org"}]}
              {:userid "user1" :name "What Ever" :email nil :some-attr "some value"}]
             (sort-by :userid (rems.db.users/get-users))))))

  (testing "survives partial user settings"
    (rems.db.user-settings/update-user-settings! "user1" {:language :fi}) ; missing notification-email

    (is (= {:userid "user1"
            :name "What Ever"
            :email nil
            :some-attr "some value"}
           (rems.db.users/get-user "user1")))

    (is (= {:language :fi :notification-email nil} (rems.db.user-settings/get-user-settings "user1"))
        "default is returned for notification-email")

    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "user1" {:language :en}))
        "settings can be updated")

    (is (= {:userid "user1"
            :name "What Ever"
            :email nil
            :some-attr "some value"}
           (rems.db.users/get-user "user1")))

    (is (= {:language :en :notification-email nil} (rems.db.user-settings/get-user-settings "user1"))
        "default is returned for notification-email"))

  (testing "update user"
    (rems.db.users/add-user! "user1" {:name "new name"})
    (is (= {:userid "user1"
            :name "new name"
            :email nil}
           (rems.db.users/get-user "user1")))

    (rems.db.users/edit-user! "user1" {:name "newer name"
                                       :email "foo@example.com"})
    (is (= {:userid "user1"
            :name "newer name"
            :email "foo@example.com"}
           (rems.db.users/get-user "user1")))))
