(ns rems.administration.test-create-catalogue-item
  (:require [clojure.test :refer [deftest is testing]]
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
                     :organization {:organization/id "organization1"}}
              :categories [{:category/id 1
                            :category/title {:fi "fi" :en "en" :sv "sv"}
                            :category/description {:fi "fi" :en "en" :sv "sv"}
                            :category/children []}]}
        languages [:en :fi]]

    (testing "valid form"
      (is (= {:wfid 123
              :resid 456
              :form 789
              :organization {:organization/id "organization1"}
              :localizations {:en {:title "en title"
                                   :infourl "hello"}
                              :fi {:title "fi title"
                                   :infourl nil}}
              :categories [{:category/id 1}]}
             (build-request form languages))))

    (testing "missing form"
      (is (= {:wfid 123
              :resid 456
              :form nil
              :organization {:organization/id "organization1"}
              :localizations {:en {:title "en title"
                                   :infourl "hello"}
                              :fi {:title "fi title"
                                   :infourl nil}}
              :categories [{:category/id 1}]}
             (build-request (assoc form :form nil)
                            languages))))

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
                               languages))))))

(deftest resource-label-test
  (testing "resource-label"
    (testing "show license names for duplicate resources"
      (is (= "y ([:t.administration/org]: NBN en) ([:t.administration/licenses]: Performance License, Second License)"
             (resource-label {:resid "y"
                              :licenses [{:localizations {:en {:title "Performance License"}
                                                          :fi {:title "Performance License FI"}
                                                          :sv {:title "Performance License SV"}}}
                                         {:localizations {:en {:title "Second License"}
                                                          :fi {:title "Second License FI"}
                                                          :sv {:title "Second License SV"}}}]
                              :organization {:organization/id "nbn"
                                             :organization/short-name {:en "NBN en"
                                                                       :fi "NBN fi"
                                                                       :sv "NBN sv"}}} :en {"y" 2})))
      (is (= "y ([:t.administration/org]: NBN fi) ([:t.administration/licenses]: Performance License FI, Second License FI)"
             (resource-label {:resid "y"
                              :licenses [{:localizations {:en {:title "Performance License"}
                                                          :fi {:title "Performance License FI"}
                                                          :sv {:title "Performance License SV"}}}
                                         {:localizations {:en {:title "Second License"}
                                                          :fi {:title "Second License FI"}
                                                          :sv {:title "Second License SV"}}}]
                              :organization {:organization/id "nbn"
                                             :organization/short-name {:en "NBN en"
                                                                       :fi "NBN fi"
                                                                       :sv "NBN sv"}}} :fi {"y" 2}))))
    (testing "don't show license names for unique resources"
      (is (= "x ([:t.administration/org]: NBN en)"
             (resource-label {:resid "x"
                              :licenses [{:localizations {:en {:title "Performance License"}}}
                                         {:localizations {:en {:title "Second License"}}}]
                              :organization {:organization/id "nbn"
                                             :organization/short-name
                                             {:en "NBN en"
                                              :fi "NBN fi"
                                              :sv "NBN sv"}}
                              :owneruserid "owner"} :en {"y" 2})))
      (is (= "y ([:t.administration/org]: NBN en)"
             (resource-label {:resid "y"
                              :licenses [{:localizations {:en {:title "Performance License"}
                                                          :fi {:title "Performance License FI"}
                                                          :sv {:title "Performance License SV"}}}
                                         {:localizations {:en {:title "Second License"}
                                                          :fi {:title "Second License FI"}
                                                          :sv {:title "Second License SV"}}}]
                              :organization {:organization/id "nbn"
                                             :organization/short-name {:en "NBN en"
                                                                       :fi "NBN fi"
                                                                       :sv "NBN sv"}}} :en {"y" 1})))
      (is (= "z" (resource-label {:resid "z"} :en {"z" 1}))))
    (testing "don't show organization if not available"
      (is (= "z" (resource-label {:resid "z"} :en {"y" 2})))
      (is (= "z" (resource-label {:resid "z"} :en {"z" 1}))))))
