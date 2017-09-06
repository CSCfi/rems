(ns rems.test.actions
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.actions :as actions]
            [rems.context :as context]
            [rems.test.tempura :refer [fake-tempura-fixture]]))

(use-fixtures :once fake-tempura-fixture)

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

(defn check-data [rows]
  (is (= 3 (count rows)))
  (check-row-text (nth rows 0) "1")
  (check-row-text (nth rows 1) "2")
  (check-row-text (nth rows 2) "3"))

(def data [{:id 2 :catalogue-item {:title "A"} :applicantuserid "tester" :handled (time/date-time 2017 8 17)}
           {:id 1 :catalogue-item {:title "B"} :applicantuserid "tester" :handled (time/date-time 2017 8 16)}
           {:id 3 :catalogue-item {:title "C"} :applicantuserid "tester" :handled (time/date-time 2017 8 18)}])

(deftest test-actions
  (let [c (#'rems.actions/approvals data)
        rows (hiccup-find [:tr.action] c)
        c2 (#'rems.actions/handled-approvals data)
        rows2 (hiccup-find [:tr.action] c2)]
    (check-data rows)
    (check-data rows2)))

(deftest test-reviews
  (let [c (#'rems.actions/reviews data)
        rows (hiccup-find [:tr.action] c)
        c2 (#'rems.actions/handled-reviews data)
        rows2 (hiccup-find [:tr.action] c2)]
    (check-data rows)
    (check-data rows2)))

(deftest test-reviewer-selection
  (testing "When selecting to whom to send a review request,"
    (with-redefs [rems.db.core/get-users (fn []
                                           (list {:userid "alice"} {:userid "bob"} {:userid "carl"} {:userid "developer"}))
                  rems.db.users/get-user-attributes (fn [uid]
                                                      (get {"alice" {"eppn" "alice" "mail" "a@li.ce" "commonName" "Ali Ce"}
                                                            "bob" {"eppn" "bob" "mail" "b@o.b" "commonName" "B Ob"}
                                                            "carl" {"eppn" "carl" "commonName" "C Arl"}
                                                            "developer" {"eppn" "developer" "mail" "deve@lo.per" "commonName" "Deve Loper"}}
                                                           uid))]
      (binding [context/*user* {"eppn" "developer" "mail" "deve@lo.per" "commonName" "Deve Loper"}]
        (let [selectables (hiccup-find [:option] (actions/review-request-modal))
              str-selectables (str (list selectables))]
          (is (= 2 (count selectables)) "selectable list should only consist of alice and bob.")
          (is (.contains str-selectables "alice"))
          (is (.contains str-selectables "bob"))
          (is (not (.contains str-selectables "carl")) "selectables should not contain users without emails")
          (is (not (.contains str-selectables "developer")) "selectable reviewers should not contain the current approver."))))))
