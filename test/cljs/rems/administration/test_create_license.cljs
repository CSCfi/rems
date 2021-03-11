(ns rems.administration.test-create-license
  (:require [clojure.test :refer [deftest is testing]]
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
                                 nil))))
  (testing "trim license link"
    (is (= "the link" (parse-textcontent {:link "  the link\t"}
                                         "link"))))
  (testing "do not trim license text"
    (is (= "  the text\t" (parse-textcontent {:text "  the text\t"}
                                             "text")))))

(deftest build-request-test
  (let [form {:licensetype "link"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :link "en link"
                                   :text "en text"
                                   :attachment-filename "something.pdf"
                                   :attachment-id 1}
                              :fi {:title "fi title"
                                   :link "fi link"
                                   :text "fi text"
                                   :attachment-filename "something_fi.pdf"
                                   :attachment-id 2}}}
        languages [:en :fi]]
    (testing "linked license"
      (is (= {:licensetype "link"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "en link"
                                   :attachment-id 1}
                              :fi {:title "fi title"
                                   :textcontent "fi link"
                                   :attachment-id 2}}}
             (build-request (assoc-in form [:licensetype] "link")
                            languages))))
    (testing "inline license"
      (is (= {:licensetype "text"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "en text"
                                   :attachment-id 1}
                              :fi {:title "fi title"
                                   :textcontent "fi text"
                                   :attachment-id 2}}}
             (build-request (assoc-in form [:licensetype] "text")
                            languages))))

    (testing "attachment license"
      (is (= {:licensetype "attachment"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "something.pdf"
                                   :attachment-id 1}
                              :fi {:title "fi title"
                                   :textcontent "something_fi.pdf"
                                   :attachment-id 2}}}
             (build-request (assoc-in form [:licensetype] "attachment")
                            languages))))
    (testing "missing organization"
      (is (nil? (build-request (assoc-in form [:organization] nil)
                               languages))))
    (testing "missing license type"
      (is (nil? (build-request (assoc-in form [:licensetype] nil)
                               languages))))
    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:localizations :en :title] "")
                               languages)))
      (is (nil? (build-request (assoc-in form [:localizations :fi :title] "")
                               languages))))
    (testing "missing license"
      (is (nil? (build-request (assoc-in form [:localizations :en :link] "")
                               languages)))
      (is (nil? (build-request (assoc-in form [:localizations :fi :link] "")
                               languages))))
    (testing "missing language"
      (is (nil? (build-request (update-in form [:localizations] dissoc :fi)
                               languages))))))
