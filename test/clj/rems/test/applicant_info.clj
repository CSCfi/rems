(ns rems.test.applicant-info
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.applicant-info :refer :all]
            [rems.context :as context]))

(defn find-from-details [pattern]
  (hiccup-find pattern (details {"eppn" "developer" "commonName" "Deve Loper"})))

(deftest test-applicant-details
  (testing "Info without role information"
    (is (empty? (find-from-details [:h3.card-header])) "Should not see collapsible header")
    (is (empty? (find-from-details [:div.collapse])) "Shouldn't see collapsible block"))
  (testing "Info as an applicant"
    (binding [context/*roles* #{:applicant}
              context/*active-role* :applicant]
      (is (empty? (find-from-details [:h3.card-header])) "Should not see collapsible header")
      (is (empty? (find-from-details [:div.collapse])) "Shouldn't see collapsible block")))
  (testing "Info as an approver"
    (binding [context/*roles* #{:approver}
              context/*active-role* :approver]
      (is (not-empty (find-from-details [:h3.card-header])) "Collapsible header should be visible.")
      (is (not-empty (find-from-details [:div.collapse])) "Collapsible block should be visible."))))
