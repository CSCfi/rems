(ns ^:integration rems.db.test-user-secrets
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-secrets :as user-secrets]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-secrets
  (users/add-user! {:userid "user"})
  (users/add-user! {:userid "unrelated"})

  (testing "default secrets for a new user"
    (is (= nil
           (user-secrets/get-user-secrets "user"))))

  (testing "add secrets"
    (is (= {:success true}
           (user-secrets/update-user-secrets! "user" {:ega {:api-key "123"}})))
    (is (= {:ega {:api-key "123"}}
           (user-secrets/get-user-secrets "user"))))

  (testing "modify secrets"
    (is (= {:success true}
           (user-secrets/update-user-secrets! "user" {:ega {:api-key "456"}})))
    (is (= {:ega {:api-key "456"}}
           (user-secrets/get-user-secrets "user"))))

  (testing "updating with empty secrets does not change secrets"
    (is (= {:success true}
           (user-secrets/update-user-secrets! "user" {})))
    (is (= {:ega {:api-key "456"}}
           (user-secrets/get-user-secrets "user"))))

  (testing "updating with invalid secrets does not change secrets"
    (is (= {:success false}
           (user-secrets/update-user-secrets! "user" {:invalid "value"}))
        "completely invalid")
    (is (= {:success false}
           (user-secrets/update-user-secrets! "user" {:ega {:api-key "789"}
                                                      :invalid "value"}))
        "partially invalid")
    (is (= {:ega {:api-key "456"}}
           (user-secrets/get-user-secrets "user"))))

  (testing "changing one user's secrets does not change another user's secrets"
    (is (= {:success true}
           (user-secrets/update-user-secrets! "unrelated" {:ega {:api-key "789"}})))
    (is (= {:success true}
           (user-secrets/update-user-secrets! "user" {:ega {:api-key "123"}})))
    (is (= {:ega {:api-key "789"}}
           (user-secrets/get-user-secrets "unrelated")))))
