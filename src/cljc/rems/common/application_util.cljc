(ns rems.common.application-util
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(defn accepted-licenses? [application userid]
  (let [application-licenses (map :license/id (:application/licenses application))
        user-accepted-licenses (get (:application/accepted-licenses application) userid)]
    (cond (empty? application-licenses) true
          (empty? user-accepted-licenses) false
          :else (every? (set user-accepted-licenses) application-licenses))))

(defn form-fields-editable? [application]
  (contains? (:application/permissions application)
             :application.command/save-draft))

(defn get-member-name [attributes]
  (or (:name attributes)
      (:userid attributes)))

(defn get-applicant-name [application]
  (get-member-name (:application/applicant application)))

(defn applicant-and-members [application]
  (cons (:application/applicant application)
        (:application/members application)))

(defn workflow-handlers [application]
  (set (mapv :userid (get-in application [:application/workflow :workflow.dynamic/handlers]))))

(defn is-handler? [application user]
  (contains? (workflow-handlers application) user))

(defn- is-parsable-to-int? [s]
  (re-matches #"\d+" s))

(defn- str-to-int [s]
  #?(:clj (Integer/parseInt s)
     :cljs (js/parseInt s 10)))

(defn parse-sortable-external-id
  "The idea is to parse all numbers from the string to vector and then compare the values in the vector"
  [external-id]
  (when external-id
    (when-let [number-sequence (seq (re-seq #"\d+|[^\d]+" external-id))]
      (mapv (fn [str] (str-to-int (is-parsable-to-int? str)))
            number-sequence))))

(deftest test-parse-sortable-external-id
  (is (= [2020 10] (parse-sortable-external-id "2020/10")))
  (is (= ["THL/" "54" "/" "14" "." "01" "." "00" "/" "2002" "-rems/" "21"] (parse-sortable-external-id "THL/54/14.01.00/2002-rems/21")))
  (is (= [2000 1] (parse-sortable-external-id "ABC/2000.1")))
  (is (= nil (parse-sortable-external-id "abGtmk")))
  (is (= nil (parse-sortable-external-id nil))))
