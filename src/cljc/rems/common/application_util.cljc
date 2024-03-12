(ns rems.common.application-util
  (:require [clojure.set]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [find-first]]
            [rems.common.util :as util]))

(def workflow-types
  #{:workflow/default
    :workflow/decider
    :workflow/master})

(def states
  #{:application.state/approved
    :application.state/closed
    :application.state/draft
    :application.state/rejected
    :application.state/returned
    :application.state/revoked
    :application.state/submitted})
;; TODO deleted state?

(defn draft? [application]
  (= :application.state/draft (:application/state application)))

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

(defn is-handler?
  "Returns true if `userid` is currently workflow handler in `application`."
  [application userid]
  (contains? (workflow-handlers application) userid))

(def +applying-user-roles+ #{:applicant :member})
(def +handling-user-roles+ #{:handler :reviewer :decider :past-reviewer :past-decider})

;; TODO combine these functions
(defn is-applying-userid? [application userid]
  (contains? (set (map :userid (applicant-and-members application)))
             userid))

(defn is-applying-user?
  "Returns true if current user is applying for `application`.

   See `rems.common.application-util/+applying-user-roles+`"
  [application]
  (let [roles (set (:application/roles application))
        applying-roles (clojure.set/intersection +applying-user-roles+ roles)]
    (some? (seq applying-roles))))

(defn is-handling-user?
  "Returns true if current user is taking part in handling process of `application` .

   See `rems.common.application-util/+handling-user-roles+`"
  [application]
  (let [roles (set (:application/roles application))
        handling-roles (clojure.set/intersection +handling-user-roles+ roles)]
    (some? (seq handling-roles))))

(defn can-see-everything?
  "Returns true if current user has special `:see-everything` permission in `application`.

   See `docs/glossary.md`"
  [application]
  (let [permissions (set (:application/permissions application))]
    (contains? permissions :see-everything)))

(defn- parse-int-maybe [x]
  (if (re-matches #"\d+" x)
    (util/parse-int x)
    x))

(deftest test-parse-int-maybe
  (is (= 2 (parse-int-maybe "2")))
  (is (= 100 (parse-int-maybe "100")))
  (is (= "/" (parse-int-maybe "/")))
  (is (= "THL/" (parse-int-maybe "THL/")))
  (is (= ["THL/" 54 "/" 14 "." 01 "." 00 "/" 2002 "-rems/" 21] (mapv parse-int-maybe (re-seq #"\d+|[^\d]+" "THL/54/14.01.00/2002-rems/21")))))

(defn parse-sortable-external-id [external-id]
  (when external-id
    (when-let [number-sequence (seq (re-seq #"\d+|[^\d]+" external-id))]
      (mapv parse-int-maybe number-sequence))))

(deftest test-parse-sortable-external-id
  (is (= [2020 "/" 10] (parse-sortable-external-id "2020/10")))
  (is (= ["THL/" 54 "/" 14 "." 01 "." 00 "/" 2002 "-rems/" 21] (parse-sortable-external-id "THL/54/14.01.00/2002-rems/21")))
  (is (= ["ABC/" 2000 "." 1] (parse-sortable-external-id "ABC/2000.1")))
  (is (= ["abGtmk"] (parse-sortable-external-id "abGtmk")))
  (is (= nil (parse-sortable-external-id nil)))
  (is (= [[2020 "/" 1] [2020 "/" 2] [2020 "/" 10] [2020 "/" 11] [2020 "/" 12]]
         (sort (mapv parse-sortable-external-id ["2020/10" "2020/1" "2020/11" "2020/12" "2020/2"]))))
  (is (= [["ABC/" 2000 "." 1] ["ABC/" 2000 "." 2] ["ABC/" 2000 "." 3] ["ABC/" 2002 "." 0] ["ABC/" 2002 "." 2] ["ABC/" 2302 "." 0]]
         (sort (mapv parse-sortable-external-id ["ABC/2000.1" "ABC/2002.0" "ABC/2002.2" "ABC/2302.0" "ABC/2000.2" "ABC/2000.3"])))))

(defn can-redact-attachment? [attachment roles userid]
  (let [already-redacted (:attachment/redacted attachment)
        event-id (get-in attachment [:attachment/event :event/id])
        allowed-roles (:attachment/redact-roles attachment)]
    (cond
      already-redacted false

      (not event-id) false

      (= userid (get-in attachment [:attachment/user :userid]))
      true

      (empty? (clojure.set/intersection (set roles)
                                        (set allowed-roles)))
      false

      :else true)))

(deftest test-can-redact-attachment?
  (testing "redact roles and user can redact"
    (let [reviewer-attachment {:attachment/event {:event/id 1}
                               :attachment/user {:userid "reviewer"}
                               :attachment/redact-roles #{:handler}}]
      (is (can-redact-attachment? reviewer-attachment #{:reviewer} "reviewer"))
      (is (can-redact-attachment? reviewer-attachment #{:handler} "handler"))
      (is (not (can-redact-attachment? reviewer-attachment #{} "alice")))))

  (testing "only user can redact"
    (let [handler-attachment {:attachment/event {:event/id 2}
                              :attachment/user {:userid "handler"}
                              :attachment/redact-roles #{}}]
      (is (can-redact-attachment? handler-attachment #{:handler} "handler"))
      (is (not (can-redact-attachment? handler-attachment #{:handler} "assistant")))))

  (testing "attachment without event cannot be redacted"
    (let [applicant-attachment {:attachment/user {:userid "alice"}}]
      (is (not (can-redact-attachment? applicant-attachment #{} "alice")))
      (is (not (can-redact-attachment? applicant-attachment #{:handler} "handler"))))))

(defn get-last-applying-user-event [application]
  (->> application
       :application/events
       reverse
       (find-first #(is-applying-userid? application (:event/actor %)))))
