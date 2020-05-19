(ns rems.api.services.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]))

(defn- forbidden-organization? [organization]
  (let [user-organizations (map :organization/id (:organizations context/*user*))
        not-owner? (not (contains? context/*roles* :owner))
        not-handler? (not (contains? context/*roles* :handler))
        incorrect-organization? (not (contains? (set user-organizations) (:organization/id organization)))]
    (and not-owner?
         not-handler? ;; TODO: keeping old behaviour where handlers can see everything for now
         (or incorrect-organization?
             ;; XXX: Special case to forbid an organization owner with
             ;;   no organizations defined from creating or accessing items with
             ;;   no organization defined. Consider disallowing undefined
             ;;   organizations to be able to remove this.
             (and (nil? (:organization/id organization)) (empty? user-organizations))))))

(defn check-allowed-organization! [organization]
  (assert (:organization/id organization) {:error "invalid organization"
                                           :organization organization})
  (when (forbidden-organization? organization)
    (throw-forbidden (str "no access to organization " (pr-str (:organization/id organization))))))

(deftest test-forbidden-organization?
  (testing "for owner, all organizations are permitted"
    (binding [context/*user* {:organizations [{:organization/id "own organization"}]}
              context/*roles* #{:owner}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization"})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for handler, all organizations are permitted"
    (binding [context/*user* {:organizations [{:organization/id "own organization"}]}
              context/*roles* #{:handler}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization"})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for owner who is also an organization owner, all organizations are permitted"
    (binding [context/*user* {:organizations [{:organization/id "own organization"}]}
              context/*roles* #{:owner :organization-owner}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "not own organization"})))
      (is (not (forbidden-organization? {:organization/id ""})))
      (is (not (forbidden-organization? {:organization/id nil})))))

  (testing "for organization owner, only own organizations are permitted"
    (binding [context/*user* {:organizations [{:organization/id "own organization"} {:organization/id "other own organization"}]}
              context/*roles* #{:organization-owner}]
      (is (not (forbidden-organization? {:organization/id "own organization"})))
      (is (not (forbidden-organization? {:organization/id "other own organization"})))
      (is (forbidden-organization? {:organization/id "not own organization"}))
      (is (forbidden-organization? {:organization/id ""}))
      (is (forbidden-organization? {:organization/id nil}))))

  (testing "for organization owner with no organization defined, all organizations are forbidden"
    (binding [context/*user* {}
              context/*roles* #{:organization-owner}]
      (is (forbidden-organization? {:organization/id "own organization"}))
      (is (forbidden-organization? {:organization/id ""}))
      (is (forbidden-organization? {:organization/id nil})))))
