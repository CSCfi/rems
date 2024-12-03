(ns rems.administration.test-create-license
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [reagent.core :as r]
            [rems.administration.create-license :refer [parse-textcontent build-request]]
            [rems.globals]
            [rems.testing :refer [init-spa-fixture]]))

(use-fixtures :each init-spa-fixture)

(defn- set-languages! [languages] (r/rswap! rems.globals/config assoc :languages languages))

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
  (set-languages! [:en :fi])

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
                                   :attachment-id 2}}}]
    (testing "linked license"
      (is (= {:licensetype "link"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "en link"}
                              :fi {:title "fi title"
                                   :textcontent "fi link"}}}
             (build-request form))))
    (testing "inline license"
      (is (= {:licensetype "text"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "en text"}
                              :fi {:title "fi title"
                                   :textcontent "fi text"}}}
             (build-request (assoc-in form [:licensetype] "text")))))

    (testing "attachment license"
      (is (= {:licensetype "attachment"
              :organization {:organization/id "default"}
              :localizations {:en {:title "en title"
                                   :textcontent "something.pdf"
                                   :attachment-id 1}
                              :fi {:title "fi title"
                                   :textcontent "something_fi.pdf"
                                   :attachment-id 2}}}
             (build-request (assoc-in form [:licensetype] "attachment")))))
    (testing "missing organization"
      (is (nil? (build-request (assoc-in form [:organization] nil)))))
    (testing "missing license type"
      (is (nil? (build-request (assoc-in form [:licensetype] nil)))))
    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:localizations :en :title] ""))))
      (is (nil? (build-request (assoc-in form [:localizations :fi :title] "")))))
    (testing "missing license"
      (is (nil? (build-request (assoc-in form [:localizations :en :link] ""))))
      (is (nil? (build-request (assoc-in form [:localizations :fi :link] "")))))
    (testing "missing language"
      (is (nil? (build-request (update-in form [:localizations] dissoc :fi)))))))
