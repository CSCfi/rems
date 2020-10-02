(ns ^:integration rems.test-pdf
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.pdf :as pdf]
            [rems.testing-util :refer [with-fixed-time utc-fixture]]
            [rems.text :refer [with-language]]))

(use-fixtures :once
  utc-fixture
  test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-pdf-gold-standard
  (test-helpers/create-user! {:eppn "alice" :commonName "Alice Applicant" :mail "alice@example.com"})
  (test-helpers/create-user! {:eppn "beth" :commonName "Beth Applicant" :mail "beth@example.com"})
  (test-helpers/create-user! {:eppn "david" :commonName "David Decider" :mail "david@example.com"})
  (let [lic1 (test-helpers/create-license! {:license/type :link
                                            :license/title {:en "Google license"
                                                            :fi "Google-lisenssi"}
                                            :license/link {:en "http://google.com"
                                                           :fi "http://google.fi"}})
        lic2 (test-helpers/create-license! {:license/type :text
                                            :license/title {:en "Text license"
                                                            :fi "Tekstilisenssi"}
                                            :license/text {:en "Some text"
                                                           :fi "Tekstiä"}})
        ;; TODO attachment license
        resource (test-helpers/create-resource! {:resource-ext-id "pdf-resource-ext"
                                                 :license-ids [lic1 lic2]})
        form (test-helpers/create-form! {:form/title  "Form"
                                         :form/fields test-data/all-field-types-example})
        catalogue-item (test-helpers/create-catalogue-item! {:resource-id resource
                                                             :title {:en "Catalogue item"
                                                                     :fi "Katalogi-itemi"}
                                                             :form-id form})
        applicant "alice"
        application-id (test-helpers/create-application! {:actor applicant
                                                          :catalogue-item-ids [catalogue-item]
                                                          :time (time/date-time 2000)})
        handler "developer"]
    (testing "fill and submit"
      (let [attachment (:id (db/save-attachment! {:application application-id
                                                  :user handler
                                                  :filename "attachment.pdf"
                                                  :type "application/pdf"
                                                  :data (byte-array 0)}))]
        ;; two draft-saved events
        (test-helpers/fill-form! {:time (time/date-time 2000)
                                  :actor applicant
                                  :application-id application-id
                                  :field-value "pdf test"
                                  :attachment attachment
                                  :optional-fields true})
        (test-helpers/fill-form! {:time (time/date-time 2000)
                                  :actor applicant
                                  :application-id application-id
                                  :field-value "pdf test"
                                  :attachment attachment
                                  :optional-fields true}))
      (test-helpers/accept-licenses! {:time (time/date-time 2000)
                                      :actor applicant
                                      :application-id application-id})
      (test-helpers/command! {:time (time/date-time 2001)
                              :application-id application-id
                              :type :application.command/submit
                              :actor applicant}))
    (testing "add member"
      (test-helpers/command! {:time (time/date-time 2002)
                              :application-id application-id
                              :type :application.command/add-member
                              :member {:userid "beth"}
                              :actor handler}))
    (testing "decide"
      (test-helpers/command! {:time (time/date-time 2003)
                              :application-id application-id
                              :type :application.command/request-decision
                              :comment "please decide"
                              :deciders ["david"]
                              :actor handler})
      (test-helpers/command! {:time (time/date-time 2003)
                              :application-id application-id
                              :type :application.command/decide
                              :comment "I have decided"
                              :decision :approved
                              :actor "david"}))
    (testing "approve"
      (let [att1 (:id (db/save-attachment! {:application application-id
                                            :user handler
                                            :filename "file1.txt"
                                            :type "text/plain"
                                            :data (byte-array 0)}))
            att2 (:id (db/save-attachment! {:application application-id
                                            :user handler
                                            :filename "file2.pdf"
                                            :type "application/pdf"
                                            :data (byte-array 0)}))]
        (test-helpers/command! {:time (time/date-time 2003)
                                :application-id application-id
                                :type :application.command/approve
                                :comment "approved"
                                :attachments [{:attachment/id att1} {:attachment/id att2}]
                                :actor handler})))
    (testing "pdf contents"
      (is (= [{}
              [[:heading pdf/heading-style "Application 2000/1: pdf test"]
               [:paragraph "This PDF generated at" " " "2010-01-01 00:00"]
               [:paragraph "State" [:phrase ": " "Approved"]]
               [:heading pdf/heading-style "Applicants"]
               [:paragraph "Applicant" ": " "Alice Applicant (alice) <alice@example.com>"]
               [[:paragraph "Member" ": " "Beth Applicant (beth) <beth@example.com>"]]
               [:heading pdf/heading-style "Resources"]
               [:list [[:phrase "Catalogue item" " (" "pdf-resource-ext" ")"]]]]
              [[:heading pdf/heading-style "Terms of use"]
               [[:paragraph "Google license"]
                [:paragraph "Text license"]]]
              [[:heading pdf/heading-style "Application"]
               [[[:paragraph pdf/label-field-style
                  "This form demonstrates all possible field types. (This text itself is a label field.)"]
                 [:paragraph ""]]
                [[:paragraph pdf/field-style "Application title field"]
                 [:paragraph "pdf test"]]
                [[:paragraph pdf/field-style "Text field"]
                 [:paragraph "pdf test"]]
                [[:paragraph pdf/field-style "Text area"]
                 [:paragraph "pdf test"]]
                [[:paragraph pdf/header-field-style "Header"]
                 [:paragraph ""]]
                [[:paragraph pdf/field-style "Date field"]
                 [:paragraph "2002-03-04"]]
                [[:paragraph pdf/field-style "Email field"]
                 [:paragraph "user@example.com"]]
                [[:paragraph pdf/field-style "Attachment"]
                 [:paragraph "attachment.pdf"]]
                [[:paragraph pdf/field-style "Option list. Choose the first option to reveal a new field."]
                 [:paragraph "First option"]]
                [[:paragraph pdf/field-style "Conditional field. Shown only if first option is selected above."]
                 [:paragraph "pdf test"]]
                [[:paragraph pdf/field-style "Multi-select list"]
                 [:paragraph "First option"]]
                [[:paragraph pdf/label-field-style "The following field types can have a max length."]
                 [:paragraph ""]]
                [[:paragraph pdf/field-style "Text field with max length"]
                 [:paragraph "pdf test"]]
                [[:paragraph pdf/field-style "Text area with max length"]
                 [:paragraph "pdf test"]]]]
              [[:heading pdf/heading-style "Events"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant created a new application." nil nil nil]
                 [:phrase "2000-01-01 00:00" " " "Alice Applicant accepted the terms of use." nil nil nil]
                 [:phrase "2001-01-01 00:00" " " "Alice Applicant submitted the application for review." nil nil nil]
                 [:phrase "2002-01-01 00:00" " " "Developer added Beth Applicant to the application." nil nil nil]
                 [:phrase "2003-01-01 00:00" " " "Developer requested a decision from David Decider." nil "\nComment: please decide" nil]
                 [:phrase "2003-01-01 00:00" " " "David Decider filed a decision for the application." "\nDavid Decider approved the application." "\nComment: I have decided" nil]
                 [:phrase "2003-01-01 00:00" " " "Developer approved the application." nil "\nComment: approved" "\nAttachments: file1.txt, file2.pdf"]]]]]
             (with-language :en
               (fn []
                 (with-fixed-time (time/date-time 2010)
                   (fn []
                     (#'pdf/render-application (applications/get-application-for-user handler application-id)))))))))
    (testing "pdf rendering succeeds"
      (is (some?
           (with-language :en
             #(do
                  ;; uncomment this to get a pdf file to look at
                #_(pdf/application-to-pdf (applications/get-application-for-user handler application-id) "/tmp/example-application.pdf")
                (pdf/application-to-pdf-bytes (applications/get-application-for-user handler application-id)))))))))
