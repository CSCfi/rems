(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn forbidden-organization? [organization]
  (let [user-organizations (:organizations context/*user*)
        not-owner? (not (contains? context/*roles* :owner))
        not-handler? (not (contains? context/*roles* :handler))
        incorrect-organization? (not (contains? (set user-organizations) organization))]
    (and not-owner?
         not-handler? ;; TODO: keeping old behaviour where handlers can see everything for now
         (or incorrect-organization?
             ;; XXX: Special case to forbid an organization owner with
             ;;   no organizations defined from creating or accessing items with
             ;;   no organization defined. Consider disallowing undefined
             ;;   organizations to be able to remove this.
             (and (nil? organization) (empty? user-organizations))))))

(defn check-allowed-organization! [organization]
  (when (forbidden-organization? organization)
    (throw-forbidden (str "no access to organization " (pr-str organization)))))

(deftest test-forbidden-organization?
  (testing "for owner, all organizations are permitted"
    (binding [context/*user* {:organizations ["own organization"]}
              context/*roles* #{:owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "not own organization")))
      (is (not (forbidden-organization? "")))
      (is (not (forbidden-organization? nil)))))

  (testing "for handler, all organizations are permitted"
    (binding [context/*user* {:organizations ["own organization"]}
              context/*roles* #{:handler}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "not own organization")))
      (is (not (forbidden-organization? "")))
      (is (not (forbidden-organization? nil)))))

  (testing "for owner who is also an organization owner, all organizations are permitted"
    (binding [context/*user* {:organizations ["own organization"]}
              context/*roles* #{:owner :organization-owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "not own organization")))
      (is (not (forbidden-organization? "")))
      (is (not (forbidden-organization? nil)))))

  (testing "for organization owner, only own organizations are permitted"
    (binding [context/*user* {:organizations ["own organization" "other own organization"]}
              context/*roles* #{:organization-owner}]
      (is (not (forbidden-organization? "own organization")))
      (is (not (forbidden-organization? "other own organization")))
      (is (forbidden-organization? "not own organization"))
      (is (forbidden-organization? ""))
      (is (forbidden-organization? nil))))

  (testing "for organization owner with no organization defined, all organizations are forbidden"
    (binding [context/*user* {}
              context/*roles* #{:organization-owner}]
      (is (forbidden-organization? "own organization"))
      (is (forbidden-organization? ""))
      (is (forbidden-organization? nil)))))
