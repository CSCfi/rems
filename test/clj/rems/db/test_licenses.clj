(ns rems.db.test-licenses
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.db.licenses :refer :all]))

(deftest test-localize-licenses
  (with-redefs [db/get-license-localizations (fn [] [{:licid 1 :langcode "en" :title "en title 1" :textcontent "en content 1" :attachmentid 1}
                                                     {:licid 1 :langcode "fi" :title "fi title 1" :textcontent "fi content 1" :attachmentid 1}
                                                     {:licid 2 :langcode "fi" :title "fi title 2" :textcontent "fi content 2" :attachmentid 1}])]
    (is (= [{:id 1,
             :type "text",
             :localizations {:en {:title "en title 1", :textcontent "en content 1", :attachment-id 1},
                             :fi {:title "fi title 1", :textcontent "fi content 1", :attachment-id 1}}}
            {:id 2,
             :type "link",
             :localizations {:fi {:title "fi title 2", :textcontent "fi content 2", :attachment-id 1}}}]
           (#'rems.db.licenses/localize-licenses
            [{:id 1 :type "text"}
             {:id 2 :type "link"}])))))

(deftest test-get-all-licenses
  (with-redefs [db/get-license-localizations (constantly [])
                db/get-all-licenses (fn [] [{:id :normal :organization "nbn" :enabled true :archived false}
                                            {:id :normal2 :enabled true :archived false}
                                            {:id :disabled :enabled false :archived false}
                                            {:id :archived :organization "hus" :enabled true :archived true}])]
    (testing "filters"
      (is (= [:normal :normal2 :disabled :archived]
             (map :id (get-all-licenses {}))))
      (is (= [:normal :normal2 :disabled]
             (map :id (get-all-licenses {:archived false}))))
      (is (= [:archived]
             (map :id (get-all-licenses {:archived true}))))
      (is (= [:disabled]
             (map :id (get-all-licenses {:enabled false}))))
      (is (= [:normal :normal2 :archived]
             (map :id (get-all-licenses {:enabled true}))))
      (is (= [:normal :normal2]
             (map :id (get-all-licenses {:archived false :enabled true})))))))
