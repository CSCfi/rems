(ns rems.test.events
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.context :as context]
            [rems.events :as events]
            [rems.test.tempura :refer [fake-tempura-fixture]]))

(use-fixtures :once fake-tempura-fixture)

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
        (let [selectables (hiccup-find [:option] (events/review-request-button 1))
              str-selectables (str (list selectables))]
          (is (= 2 (count selectables)) "selectable list should only consist of alice and bob.")
          (is (.contains str-selectables "alice"))
          (is (.contains str-selectables "bob"))
          (is (not (.contains str-selectables "carl")) "selectables should not contain users without emails")
          (is (not (.contains str-selectables "developer")) "selectable reviewers should not contain the current approver."))))))
