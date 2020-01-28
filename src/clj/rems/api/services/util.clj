(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn forbidden-organization? [user-id organization]
  ;; XXX: If called multiple times with the same user-id, queries database
  ;;      again each time.
  (let [user (users/get-user user-id)
        not-owner? (not (contains? (roles/get-roles user-id) :owner))
        incorrect-organization? (not= organization (:organization user))]
    (and not-owner?
         (or incorrect-organization?
             ;; XXX: Special case to forbid an organization owner with
             ;;   no organization defined from creating or accessing items with
             ;;   no organization defined. Consider disallowing undefined
             ;;   organizations to be able to remove this.
             (and (nil? organization) (nil? (:organization user)))))))

(defn forbidden-organization-error? [user-id organization]
  (when (forbidden-organization? user-id organization)
    {:success false
     :errors [{:type :t.administration.errors/forbidden-organization}]}))

(deftest test-forbidden-organization?
  (with-redefs [roles/get-roles {"owner" #{:owner}
                                 "organization-owner" #{:organization-owner}
                                 "owner-and-organization-owner" #{:owner :organization-owner}
                                 "organization-owner-with-no-organization" #{:organization-owner}}
                users/get-user {"owner" {:eppn "owner"
                                         :organization "own organization"}
                                "organization-owner" {:eppn "organization-owner"
                                                      :organization "own organization"}
                                "owner-and-organization-owner" {:eppn "owner-and-organization-owner"
                                                                :organization "own organization"}
                                "organization-owner-with-no-organization" {:eppn "organization-owner-with-no-organization"}}]
    (testing "for owner, all organizations are permitted"
      (is (not (forbidden-organization? "owner" "own organization")))
      (is (not (forbidden-organization? "owner" "not own organization"))))

    (testing "for organization owner, only own organization is permitted"
      (is (not (forbidden-organization? "organization-owner" "own organization")))
      (is (forbidden-organization? "organization-owner" "not own organization")))

    (testing "for organization owner with no organization, all organizations are forbidden"
      (is (forbidden-organization? "organization-owner-with-no-organization" "own organization")))

    (testing "for owner who is also an organization owner, all organizations are permitted"
      (is (not (forbidden-organization? "owner-and-organization-owner" "own organization")))
      (is (not (forbidden-organization? "owner-and-organization-owner" "not own organization"))))

    (testing "no organization defined for the created item or organization field left empty"
      (is (not (forbidden-organization? "owner" nil)))
      (is (not (forbidden-organization? "owner" "")))
      (is (forbidden-organization? "organization-owner" nil))
      (is (forbidden-organization? "organization-owner" ""))
      (is (forbidden-organization? "organization-owner-with-no-organization" nil))
      (is (forbidden-organization? "organization-owner-with-no-organization" "")))))
