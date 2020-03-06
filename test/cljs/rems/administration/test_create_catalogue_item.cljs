(ns rems.administration.test-create-catalogue-item
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-catalogue-item :refer [build-request]]))

(deftest build-request-test
  (let [form {:title {:en "en title"
                      :fi "fi title"}
              :infourl {:en "hello"
                        :fi ""}
              :organization "organization1"
              :workflow {:id 123
                         :organization "organization1"}
              :resource {:id 456
                         :organization "organization1"}
              :form {:form/id 789
                     :form/organization "organization1"}}
        languages [:en :fi]]

    (testing "valid form"
      (is (= {:wfid 123
              :resid 456
              :form 789
              :organization "organization1"
              :localizations {:en {:title "en title"
                                   :infourl "hello"}
                              :fi {:title "fi title"
                                   :infourl nil}}}
             (build-request form languages))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title :en] "")
                               languages)))
      (is (nil? (build-request (assoc-in form [:title :fi] "")
                               languages))))

    (testing "missing organization"
      (is (nil? (build-request (dissoc form :organization)
                               languages))))

    (testing "missing workflow"
      (is (nil? (build-request (assoc form :workflow nil)
                               languages))))
    (testing "missing resource"
      (is (nil? (build-request (assoc form :resource nil)
                               languages))))
    (testing "missing form"
      (is (nil? (build-request (assoc form :form nil)
                               languages))))))
