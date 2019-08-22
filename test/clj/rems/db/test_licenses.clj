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
             :title "default title 1",
             :textcontent "default content 1",
             :localizations {:en {:title "en title 1", :textcontent "en content 1", :attachment-id 1},
                             :fi {:title "fi title 1", :textcontent "fi content 1", :attachment-id 1}}}
            {:id 2,
             :type "link",
             :title "default title 2",
             :textcontent "default content 2",
             :localizations {:fi {:title "fi title 2", :textcontent "fi content 2", :attachment-id 1}}}]
           (#'rems.db.licenses/localize-licenses
            [{:id 1 :type "text" :title "default title 1" :textcontent "default content 1"}
             {:id 2 :type "link" :title "default title 2" :textcontent "default content 2"}])))))

(deftest test-get-all-licenses
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        expired-license-end (time/plus yesterday (time/hours 1))
        just-created-license-start (time/minus today (time/hours 1))]
    (with-redefs [db/get-license-localizations (constantly [])
                  db/get-all-licenses (fn [] [{:id :always :start nil :end nil}
                                              {:id :always2 :start nil :end nil}
                                              {:id :expired :start nil :end expired-license-end}
                                              {:id :just-created :start just-created-license-start :end nil}])]
      (testing "expired field is added"
        (is (= [{:id :always :expired false}
                {:id :always2 :expired false}
                {:id :expired :expired true}
                {:id :just-created :expired false}]
               (map #(select-keys % [:id :expired]) (get-all-licenses {})))))
      (testing "filters"
        (is (= [:expired]
               (map :id (get-all-licenses {:expired true}))))
        (is (= [:always :always2 :just-created]
               (map :id (get-all-licenses {:expired false}))))))))
