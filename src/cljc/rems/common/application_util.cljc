(ns rems.common.application-util
  (:require [clojure.test :refer [deftest is]]
            [rems.common.util :as util]))

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

(defn- parse-int-maybe [x]
  (if (re-matches #"\d+" x)
    (util/parse-int x)
    x))

(deftest test-parse-int-maybe
  (is (= 2 (parse-int-maybe "2")))
  (is (= 100 (parse-int-maybe "100")))
  (is (= "/" (parse-int-maybe "/")))
  (is (= "THL/" (parse-int-maybe "THL/")))
  (is (= ["THL/" 54 "/" 14  "." 01 "." 00 "/" 2002 "-rems/" 21] (mapv parse-int-maybe (re-seq #"\d+|[^\d]+" "THL/54/14.01.00/2002-rems/21")))))

(defn parse-sortable-external-id [external-id]
  (when external-id
    (when-let [number-sequence (seq (re-seq #"\d+|[^\d]+" external-id))]
      (mapv parse-int-maybe number-sequence))))

(deftest test-parse-sortable-external-id
  (is (= [2020 "/" 10] (parse-sortable-external-id "2020/10")))
  (is (= ["THL/" 54 "/" 14  "." 01 "." 00 "/" 2002 "-rems/" 21] (parse-sortable-external-id "THL/54/14.01.00/2002-rems/21")))
  (is (= ["ABC/" 2000 "." 1] (parse-sortable-external-id "ABC/2000.1")))
  (is (= ["abGtmk"] (parse-sortable-external-id "abGtmk")))
  (is (= nil (parse-sortable-external-id nil)))
  (is (= [[2020 "/" 1] [2020 "/" 2] [2020 "/" 10] [2020 "/" 11] [2020 "/" 12]]
         (sort (mapv parse-sortable-external-id ["2020/10" "2020/1" "2020/11" "2020/12" "2020/2"]))))
  (is (= [["ABC/" 2000 "." 1] ["ABC/" 2000 "." 2] ["ABC/" 2000 "." 3] ["ABC/" 2002 "." 0] ["ABC/" 2002 "." 2]  ["ABC/" 2302 "." 0]]
         (sort (mapv parse-sortable-external-id ["ABC/2000.1" "ABC/2002.0" "ABC/2002.2"  "ABC/2302.0" "ABC/2000.2" "ABC/2000.3"])))))

