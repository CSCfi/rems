(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.context :as context]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn forbidden-organization? [organization]
  (let [user-organization (:organization context/*user*)
        not-owner? (not (contains? context/*roles* :owner))
        incorrect-organization? (not= organization user-organization)]
    (and not-owner?
         (or incorrect-organization?
             ;; XXX: Special case to forbid an organization owner with
             ;;   no organization defined from creating or accessing items with
             ;;   no organization defined. Consider disallowing undefined
             ;;   organizations to be able to remove this.
             (and (nil? organization) (nil? user-organization))))))

(defn forbidden-organization-error [organization]
  (when (forbidden-organization? organization)
    {:success false
     :errors [{:type :t.administration.errors/forbidden-organization}]}))

(deftest test-forbidden-organization?
  (testing "for owner, all organizations are permitted"
    (binding [context/*user* {:organization "own organization"}
              context/*roles* #{:owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "not own organization")))
      (is (not (forbidden-organization? "")))
      (is (not (forbidden-organization? nil)))))

  (testing "for owner who is also an organization owner, all organizations are permitted"
    (binding [context/*user* {:organization "own organization"}
              context/*roles* #{:owner :organization-owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "not own organization")))
      (is (not (forbidden-organization? "")))
      (is (not (forbidden-organization? nil)))))

  (testing "for organization owner, only own organization is permitted"
    (binding [context/*user* {:organization "own organization"}
              context/*roles* #{:organization-owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (forbidden-organization? "not own organization"))
      (is (forbidden-organization? ""))
      (is (forbidden-organization? nil))))

  (testing "for organization owner with no organization defined, all organizations are forbidden"
    (binding [context/*user* {}
              context/*roles* #{:organization-owner}]
      (is (forbidden-organization? "own organization"))
      (is (forbidden-organization? ""))
      (is (forbidden-organization? nil)))))
