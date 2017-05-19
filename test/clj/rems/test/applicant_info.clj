(ns rems.test.applicant-info
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.applicant-info :refer :all]
            [rems.test.tempura :refer [with-fake-tempura]]
            [rems.context :as context]))

(defn find-from-details [pattern]
  (hiccup-find pattern (details "applicant-info" {"eppn" "developer" "commonName" "Deve Loper"})))

(defn children-of [hiccups]
  (remove nil? (mapcat (partial drop 2) hiccups)))

(deftest test-applicant-details
  (with-fake-tempura
    (testing "Info without role information"
      (is (not-empty (find-from-details [:.card-header])) "Should see collapsible header")
      (is (empty? (children-of (find-from-details [:.collapse-content]))) "Shouldn't see collapsible block"))
    (testing "Info as an applicant"
      (binding [context/*roles* #{:applicant}
                context/*active-role* :applicant]
        (is (not-empty (find-from-details [:.card-header])) "Should see collapsible header")
        (is (empty? (children-of (find-from-details [:.collapse-content]))) "Shouldn't see collapsible block")))
    (testing "Info as an approver"
      (binding [context/*roles* #{:approver}
                context/*active-role* :approver]
        (is (not-empty (find-from-details [:.card-header])) "Collapsible header should be visible.")
        (is (not-empty (children-of (find-from-details [:.collapse-content]))) "Collapsible block should be visible.")))))
