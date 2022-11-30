(ns rems.service.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.organizations]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]
            [rems.util :refer [getx-user-id]]))

(defn- may-edit-organization? [organization]
  (let [owner? (contains? context/*roles* :owner)
        organization-owners (set (map :userid (:organization/owners organization)))
        organization-owner? (contains? organization-owners (getx-user-id))]
    (or owner?
        organization-owner?)))

(defn check-allowed-organization! [organization]
  (assert (:organization/id organization) {:error "invalid organization"
                                           :organization organization})
  (when-not (may-edit-organization? (rems.db.organizations/get-organization-by-id-raw
                                     (:organization/id organization)))
    (throw-forbidden (str "no access to organization " (pr-str (:organization/id organization))))))

(deftest test-may-edit-organization?
  (let [org-empty {:organization/id ""}
        org-nobody {:organization/id "organization with no owners" :organization/owners []}
        org-bob {:organization/id "organization owned by bob" :organization/owners [{:userid "bob"}]}
        org-carl {:organization/id "organization owned by bob" :organization/owners [{:userid "carl"}]}
        org-bob-carl {:organization/id "organization owned by bob and carl" :organization/owners [{:userid "bob"} {:userid "carl"}]}]
    (testing "for owner, all organizations are permitted"
      (binding [context/*user* {:userid "owner"}
                context/*roles* #{:owner}]
        (is (may-edit-organization? org-empty))
        (is (may-edit-organization? org-nobody))
        (is (may-edit-organization? org-bob))
        (is (may-edit-organization? org-carl))
        (is (may-edit-organization? org-bob-carl))))

    (testing "for owner who is also an organization owner, all organizations are permitted"
      (binding [context/*user* {:userid "bob"}
                context/*roles* #{:owner}]
        (is (may-edit-organization? org-empty))
        (is (may-edit-organization? org-nobody))
        (is (may-edit-organization? org-bob))
        (is (may-edit-organization? org-carl))
        (is (may-edit-organization? org-bob-carl))))

    (testing "for organization owner, only own organizations are permitted"
      (binding [context/*user* {:userid "bob"}
                context/*roles* #{}]
        (is (not (may-edit-organization? org-empty)))
        (is (not (may-edit-organization? org-nobody)))
        (is (may-edit-organization? org-bob))
        (is (not (may-edit-organization? org-carl)))
        (is (may-edit-organization? org-bob-carl)))

      (testing ", even if they are a handler"
        (binding [context/*user* {:userid "bob"}
                  context/*roles* #{:handler}]
          (is (not (may-edit-organization? org-empty)))
          (is (not (may-edit-organization? org-nobody)))
          (is (may-edit-organization? org-bob))
          (is (not (may-edit-organization? org-carl)))
          (is (may-edit-organization? org-bob-carl)))))

    (testing "for other user, no organizations are permitted"
      (binding [context/*user* {:userid "alice"}
                context/*roles* #{}]
        (is (not (may-edit-organization? org-empty)))
        (is (not (may-edit-organization? org-nobody)))
        (is (not (may-edit-organization? org-bob)))
        (is (not (may-edit-organization? org-carl)))
        (is (not (may-edit-organization? org-bob-carl)))))))
