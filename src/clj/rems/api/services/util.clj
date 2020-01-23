(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn forbidden-organization? [user-id organization]
  (let [user (users/get-user user-id)]
    (when (and (contains? (roles/get-roles user-id) :organization-owner)
               (or (not= organization (:organization user))
                   ;; XXX: Special case to forbid an organization owner with
                   ;;   no organization defined from creating items with
                   ;;   no organization defined. Consider disallowing undefined
                   ;;   organizations to be able to remove this.
                   (and (nil? organization) (nil? (:organization user)))))
      {:success false
       :errors [{:type :t.administration.errors/forbidden-organization}]})))

(deftest test-forbidden-organization?
  (with-redefs [roles/get-roles {"owner" #{:owner}
                                 "organization-owner" #{:organization-owner}
                                 "organization-owner-with-no-organization" #{:organization-owner}}
                users/get-user {"owner" {:eppn "owner"
                                         :organization "own organization"}
                                "organization-owner" {:eppn "organization-owner"
                                                      :organization "own organization"}
                                "organization-owner-with-no-organization" {:eppn "organization-owner-with-no-organization"}}]
    (let [forbidden-error {:success false
                           :errors [{:type :t.administration.errors/forbidden-organization}]}]
      (testing "for owner, all organizations are permitted"
        (is (nil? (forbidden-organization? "owner" "own organization")))
        (is (nil? (forbidden-organization? "owner" "not own organization"))))

      (testing "for organization owner, only own organization is permitted"
        (is (nil? (forbidden-organization? "organization-owner" "own organization")))
        (is (= forbidden-error (forbidden-organization? "organization-owner" "not own organization"))))

    (testing "for organization owner with no organization, all organizations are forbidden"
      (is (= forbidden-error (forbidden-organization? "organization-owner-with-no-organization" "own organization"))))

    (testing "no organization defined for the created item or organization field left empty"
      (is (nil? (forbidden-organization? "owner" nil)))
      (is (nil? (forbidden-organization? "owner" "")))
      (is (= forbidden-error (forbidden-organization? "organization-owner" nil)))
      (is (= forbidden-error (forbidden-organization? "organization-owner" "")))
      (is (= forbidden-error (forbidden-organization? "organization-owner-with-no-organization" nil)))
      (is (= forbidden-error (forbidden-organization? "organization-owner-with-no-organization" "")))))))
