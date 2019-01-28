(ns rems.test.administration.license
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.create-license :refer [parse-textcontent build-request]]))

(deftest parse-textcontent-test
  (testing "linked license"
    (is (= "the link" (parse-textcontent {:link "the link"
                                          :text "the text"}
                                         "link"))))
  (testing "inline license"
    (is (= "the text" (parse-textcontent {:link "the link"
                                          :text "the text"}
                                         "text"))))
  (testing "missing license type"
    (is (nil? (parse-textcontent {:link "the link"
                                  :text "the text"}
                                 nil)))))

(deftest build-request-test
  (let [form {:licensetype "link"
              :localizations {:en {:title "en title"
                                   :link "en link"
                                   :text "en text"}
                              :fi {:title "fi title"
                                   :link "fi link"
                                   :text "fi text"}}}
        default-language :en
        languages [:en :fi]]
    (testing "linked license"
      (is (= {:title "en title"
              :licensetype "link"
              :textcontent "en link"
              :localizations {:en {:title "en title"
                                   :textcontent "en link"}
                              :fi {:title "fi title"
                                   :textcontent "fi link"}}}
             (build-request (assoc-in form [:licensetype] "link")
                            default-language
                            languages))))
    (testing "inline license"
      (is (= {:title "en title"
              :licensetype "text"
              :textcontent "en text"
              :localizations {:en {:title "en title"
                                   :textcontent "en text"}
                              :fi {:title "fi title"
                                   :textcontent "fi text"}}}
             (build-request (assoc-in form [:licensetype] "text")
                            default-language
                            languages))))
    (testing "missing license type"
      (is (nil? (build-request (assoc-in form [:licensetype] nil)
                               default-language
                               languages))))
    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:localizations :en :title] "")
                               default-language
                               languages)))
      (is (nil? (build-request (assoc-in form [:localizations :fi :title] "")
                               default-language
                               languages))))
    (testing "missing license"
      (is (nil? (build-request (assoc-in form [:localizations :en :link] "")
                               default-language
                               languages)))
      (is (nil? (build-request (assoc-in form [:localizations :fi :link] "")
                               default-language
                               languages))))
    (testing "missing language"
      (is (nil? (build-request (update-in form [:localizations] dissoc :fi)
                               default-language
                               languages))))))
