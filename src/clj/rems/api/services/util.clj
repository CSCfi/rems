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
  (testing "for owner, all organizations are permitted"
    (binding [context/*user* {:eppn "x"}
              context/*roles* #{:owner}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization"})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for handler, all organizations are permitted"
    (binding [context/*user* {:eppn "x"}
              context/*roles* #{:handler}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization"})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for owner who is also an organization owner, all organizations are permitted"
    (binding [context/*user* {:eppn "x"}
              context/*roles* #{:owner}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization" :organization/owners [{:userid "x"}]})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for organization owner, only own organizations are permitted"
    (binding [context/*user* {:eppn "x"}
              context/*roles* #{}]
      (is (not (forbidden-organization? {:organization/id "own organization" :organization/owners [{:userid "x"}]})))
      (is (not (forbidden-organization? {:organization/id "other own organization" :organization/owners [{:userid "y"} {:userid "x"}]})))
      (is (forbidden-organization? {:organization/id "not own organization" :organization/owners [{:userid "y"}]}))
      (is (forbidden-organization? {:organization/id ""}))
      (is (forbidden-organization? {:organization/id nil})))))
