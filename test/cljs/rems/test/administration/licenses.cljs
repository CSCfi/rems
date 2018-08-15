(ns rems.test.administration.licenses
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.license :refer [parse-textcontent]]))

(deftest parse-textcontent-test
  (testing "linked license"
    (is (= "the link" (parse-textcontent {:licensetype "link"
                                          :link "the link"
                                          :text "the text"}))))
  (testing "inline license"
    (is (= "the text" (parse-textcontent {:licensetype "text"
                                          :link "the link"
                                          :text "the text"}))))
  (testing "no type selected"
    (is (= nil (parse-textcontent {:link "the link"
                                   :text "the text"})))))
