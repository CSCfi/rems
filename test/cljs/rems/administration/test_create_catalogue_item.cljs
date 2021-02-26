(ns rems.administration.test-create-catalogue-item
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-catalogue-item :refer [build-request resource-label]]))

(deftest build-request-test
  (let [form {:title {:en "en title"
                      :fi "fi title"}
              :infourl {:en "hello"
                        :fi ""}
              :organization {:organization/id "organization1"}
              :workflow {:id 123
                         :organization {:organization/id "organization1"}}
              :resource {:id 456
                         :organization {:organization/id "organization1"}}
              :form {:form/id 789
                     :organization {:organization/id "organization1"}}}
        languages [:en :fi]]

    (testing "valid form"
      (is (= {:wfid 123
              :resid 456
              :form 789
              :organization {:organization/id "organization1"}
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

(deftest resource-label-test
  (let [resources [{:resid "x"
                    :licenses [{:localizations {:en {:title "Performance License"}}}
                               {:localizations {:en {:title "Second License"}}}]
                    :organization {:organization/id "nbn"
                                   :organization/short-name
                                   {:en "NBN en"
                                    :fi "NBN fi"
                                    :sv "NBN sv"}}
                    :owneruserid "owner"}
                   {:resid "y"
                    :licenses [{:localizations {:en {:title "Performance License"}
                                                :fi {:title "Performance License FI"}
                                                :sv {:title "Performance License SV"}}}
                               {:localizations {:en {:title "Second License"}
                                                :fi {:title "Second License FI"}
                                                :sv {:title "Second License SV"}}}]
                    :organization {:organization/id "nbn"
                                   :organization/short-name {:en "NBN en"
                                                             :fi "NBN fi"
                                                             :sv "NBN sv"}}}
                   {:resid "y"
                    :licenses [{:localizations {:en {:title "Performance License"}
                                                :fi {:title "Performance License FI"}
                                                :sv {:title "Performance License SV"}}}]}
                   {:resid "u"}
                   {:resid "z"}]
        counts (frequencies (map :resid resources))]
    (testing "resource-label"
      (is (= ["y ([:t.administration/org]: NBN en) ([:t.administration/licenses]: Performance License, Second License)"
              "y ([:t.administration/licenses]: Performance License)"]
             (map #(resource-label % :en counts) [{:resid "y"
                                                   :licenses [{:localizations {:en {:title "Performance License"}
                                                                               :fi {:title "Performance License FI"}
                                                                               :sv {:title "Performance License SV"}}}
                                                              {:localizations {:en {:title "Second License"}
                                                                               :fi {:title "Second License FI"}
                                                                               :sv {:title "Second License SV"}}}]
                                                   :organization {:organization/id "nbn"
                                                                  :organization/short-name {:en "NBN en"
                                                                                            :fi "NBN fi"
                                                                                            :sv "NBN sv"}}}
                                                  {:resid "y"
                                                   :licenses [{:localizations {:en {:title "Performance License"}
                                                                               :fi {:title "Performance License FI"}
                                                                               :sv {:title "Performance License SV"}}}]}])))

      (is (= "x ([:t.administration/org]: NBN en)"
             (resource-label {:resid "x"
                              :licenses [{:localizations {:en {:title "Performance License"}}}
                                         {:localizations {:en {:title "Second License"}}}]
                              :organization {:organization/id "nbn"
                                             :organization/short-name
                                             {:en "NBN en"
                                              :fi "NBN fi"
                                              :sv "NBN sv"}}
                              :owneruserid "owner"} :en counts)))
      (is (= ["x ([:t.administration/org]: NBN en)"
              "y ([:t.administration/org]: NBN en) ([:t.administration/licenses]: Performance License, Second License)"
              "y ([:t.administration/licenses]: Performance License)"
              "u"
              "z"]
             (map #(resource-label % :en counts) resources)))
      (is (= ["x ([:t.administration/org]: NBN fi)"
              "y ([:t.administration/org]: NBN fi) ([:t.administration/licenses]: Performance License FI, Second License FI)"
              "y ([:t.administration/licenses]: Performance License FI)"
              "u"
              "z"]
             (map #(resource-label % :fi counts) resources)))
      (is (= ["x ([:t.administration/org]: NBN sv)"
              "y ([:t.administration/org]: NBN sv) ([:t.administration/licenses]: Performance License SV, Second License SV)"
              "y ([:t.administration/licenses]: Performance License SV)"
              "u"
              "z"]
             (map #(resource-label % :sv counts) resources))))))


