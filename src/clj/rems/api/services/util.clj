(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.api.services.organizations :as organizations]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]
            [rems.util :refer [getx-user-id]]))

(defn- forbidden-organization? [organization]
  (let [not-owner? (not (contains? context/*roles* :owner))
        not-handler? (not (contains? context/*roles* :handler)) ;; handlers have read-only access to all orgs
        organization-owners (set (map :userid (:organization/owners organization)))
        not-organization-owner? (not (contains? organization-owners (getx-user-id)))]
    (and not-owner?
         not-handler? ;; TODO: keeping old behaviour where handlers can see everything for now
         not-organization-owner?)))

(defn check-allowed-organization! [organization]
  (assert (:organization/id organization) {:error "invalid organization"
                                           :organization organization})
  (when (forbidden-organization? (organizations/get-organization-raw organization))
    (throw-forbidden (str "no access to organization " (pr-str (:organization/id organization))))))

(deftest test-forbidden-organization?
  (let [org-empty {:organization/id ""}
        org-nobody {:organization/id "organization with no owners" :organization/owners []}
        org-bob {:organization/id "organization owned by bob" :organization/owners [{:userid "bob"}]}
        org-carl {:organization/id "organization owned by bob" :organization/owners [{:userid "carl"}]}
        org-bob-carl {:organization/id "organization owned by bob and carl" :organization/owners [{:userid "bob"} {:userid "carl"}]}]
    (testing "for owner, all organizations are permitted"
      (binding [context/*user* {:eppn "owner"}
                context/*roles* #{:owner}]
        (is (not (forbidden-organization? org-empty)))
        (is (not (forbidden-organization? org-nobody)))
        (is (not (forbidden-organization? org-bob)))
        (is (not (forbidden-organization? org-carl)))
        (is (not (forbidden-organization? org-bob-carl)))))

    (testing "for handler, all organizations are permitted"
      (binding [context/*user* {:eppn "bob"}
                context/*roles* #{:handler}]
        (is (not (forbidden-organization? org-empty)))
        (is (not (forbidden-organization? org-nobody)))
        (is (not (forbidden-organization? org-bob)))
        (is (not (forbidden-organization? org-carl)))
        (is (not (forbidden-organization? org-bob-carl)))))

    (testing "for owner who is also an organization owner, all organizations are permitted"
      (binding [context/*user* {:eppn "bob"}
                context/*roles* #{:owner}]
        (is (not (forbidden-organization? org-empty)))
        (is (not (forbidden-organization? org-nobody)))
        (is (not (forbidden-organization? org-bob)))
        (is (not (forbidden-organization? org-carl)))
        (is (not (forbidden-organization? org-bob-carl)))))

    (testing "for organization owner, only own organizations are permitted"
      (binding [context/*user* {:eppn "bob"}
                context/*roles* #{}]
        (is (forbidden-organization? org-empty))
        (is (forbidden-organization? org-nobody))
        (is (not (forbidden-organization? org-bob)))
        (is (forbidden-organization? org-carl))
        (is (not (forbidden-organization? org-bob-carl)))))

    (testing "for other user, no organizations are permitted"
      (binding [context/*user* {:eppn "alice"}
                context/*roles* #{}]
        (is (forbidden-organization? org-empty))
        (is (forbidden-organization? org-nobody))
        (is (forbidden-organization? org-bob))
        (is (forbidden-organization? org-carl))
        (is (forbidden-organization? org-bob-carl))))))
