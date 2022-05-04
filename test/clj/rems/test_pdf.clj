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

(use-fixtures
  :once
  utc-fixture
  test-db-fixture)

(use-fixtures :each rollback-db-fixture)

;; regression test
(deftest test-pdf-empty-table
  (let [data {:application/state :draft
              :application/resources []
              :application/id 123
              :application/applicant "user"
              :application/members []
              :application/licenses []
              :application/events []
              :application/forms [{:form/fields [{:field/visible true
                                                  :field/type :table
                                                  :field/title {:en "Table field"}
                                                  :field/value []}]}]}]
    (is (= [[[:heading {:spacing-before 20} "Application"]
             [[[:paragraph {:spacing-before 8, :style :bold} "Table field"]
               [:paragraph "No rows"]]]]]
           (with-language :en
             (fn []
               (with-fixed-time (time/date-time 2010)
                 (fn []
                   (#'pdf/render-fields data)))))))
    (is (some? (with-language :en #(pdf/application-to-pdf-bytes data))))))

(deftest test-pdf-private-form-fields
  (test-helpers/create-user! {:userid "alice" :name "Alice Applicant" :email "alice@example.com"})
  (test-helpers/create-user! {:userid "carl" :name "Carl Reviewer" :email "carl@example.com"})
  (test-helpers/create-user! {:userid "david" :name "David Decider" :email "david@example.com"})
  (let [resource (test-helpers/create-resource! {:resource-ext-id "pdf-resource-ext"})
        resource-2 (test-helpers/create-resource! {:resource-ext-id "pdf-resource-2-ext"})
        wfid (test-helpers/create-workflow! {})
        form (test-helpers/create-form! {:form/internal-name  "Form"
                                         :form/external-title {:en "Form"
                                                               :fi  "Lomake"
                                                               :sv "Blankett"}
                                         :form/fields [{:field/type :text
                                                        :field/title {:en "Public text field"
                                                                      :fi "Julkinen tekstikenttä"
                                                                      :sv "Offentligt textfält"}
                                                        :field/optional false}
                                                       {:field/type :text
                                                        :field/privacy :private
                                                        :field/title {:en "Private text field"
                                                                      :fi "Yksityinen tekstikenttä"
                                                                      :sv "Privat textfält"}
                                                        :field/optional false}]})
        private-form (test-helpers/create-form! {:form/internal-name  "Private form"
                                                 :form/external-title {:en "Private form"
                                                                       :fi "Yksityinen lomake"
                                                                       :sv "Privat blankett"}
                                                 :form/fields [{:field/type :text
                                                                :field/privacy :private
                                                                :field/title {:en "Private text field"
                                                                              :fi "Yksityinen tekstikenttä"
                                                                              :sv "Privat textfält"}
                                                                :field/optional false}]})
        catalogue-item (test-helpers/create-catalogue-item! {:resource-id resource
                                                             :workflow-id wfid
                                                             :title {:en "Resource"
                                                                     :fi "Resurssi"
                                                                     :sv "Resurs"}
                                                             :form-id form})
        catalogue-item-2 (test-helpers/create-catalogue-item! {:resource-id resource-2
                                                               :workflow-id wfid
                                                               :title {:en "Resource 2"
                                                                       :fi "Resurssi 2"
                                                                       :sv "Resurs 2"}
                                                               :form-id private-form})
        applicant "alice"
        handler "developer"
        application-id (test-helpers/create-application! {:actor applicant
                                                          :catalogue-item-ids [catalogue-item catalogue-item-2]
                                                          :time (time/date-time 2000)})]
    (testing "fill and submit"
      (test-helpers/fill-form! {:time (time/date-time 2000)
                                :actor applicant
                                :application-id application-id
                                :field-value "pdf test"
                                :optional-fields true})
      (test-helpers/command! {:time (time/date-time 2001)
                              :application-id application-id
                              :type :application.command/submit
                              :actor applicant}))
    (testing "add reviewer"
      (test-helpers/command! {:time (time/date-time 2003)
                              :type :application.command/request-review
                              :application-id application-id
                              :actor handler
                              :reviewers ["carl"]
                              :comment "please have a look"}))
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

    (testing "alice should not see reviewer and decider actions"
      (is (= [{}
              [[:heading {:spacing-before 20} "Ansökan 2000/1: "]
               [:paragraph "Denna pdf skapades" " " "2010-01-01 00:00"]
               [:paragraph "Status" [:phrase ": " "Inlämnad"]]
               [:heading {:spacing-before 20} "Sökande"]
               [:paragraph "Sökande" ": " "Alice Applicant (alice) <alice@example.com>. Licenserna har accepterats: Ja"]
               []
               [:heading {:spacing-before 20} "Resurser"]
               [:list [[:phrase "Resurs" " (" "pdf-resource-ext" ")"]
                       [:phrase "Resurs 2" " (" "pdf-resource-2-ext" ")"]]]]
              [[:heading {:spacing-before 20} "Licenser"] []]
              [[[:heading {:spacing-before 20} "Blankett"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Offentligt textfält"]
                  [:paragraph "pdf test"]]
                 [[:paragraph {:spacing-before 8, :style :bold} "Privat textfält"]
                  [:paragraph "pdf test"]]]]
               [[:heading {:spacing-before 20} "Privat blankett"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Privat textfält"]
                  [:paragraph "pdf test"]]]]]
              [[:heading {:spacing-before 20} "Händelser"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant skapade ansökan 2000/1." nil nil nil]
                 [:phrase "2001-01-01 00:00" " " "Alice Applicant lämnade in ansökan." nil nil nil]]]]]
             (with-language :sv
               (fn []
                 (with-fixed-time (time/date-time 2010)
                   (fn []
                     (#'pdf/render-application (applications/get-application-for-user "alice" application-id)))))))))
    (testing "handler should see complete application"
      (is (= [{}
              [[:heading {:spacing-before 20} "Application 2000/1: "]
               [:paragraph "This PDF generated at" " " "2010-01-01 00:00"]
               [:paragraph "State" [:phrase ": " "Applied"]]
               [:heading {:spacing-before 20} "Applicants"]
               [:paragraph "Applicant" ": " "Alice Applicant (alice) <alice@example.com>. Accepted terms of use: Yes"]
               []
               [:heading {:spacing-before 20} "Resources"]
               [:list [[:phrase "Resource" " (" "pdf-resource-ext" ")"]
                       [:phrase "Resource 2" " (" "pdf-resource-2-ext" ")"]]]]
              [[:heading {:spacing-before 20} "Terms of use"] []]
              [[[:heading {:spacing-before 20} "Form"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Public text field"]
                  [:paragraph "pdf test"]]
                 [[:paragraph {:spacing-before 8, :style :bold} "Private text field"]
                  [:paragraph "pdf test"]]]]
               [[:heading {:spacing-before 20} "Private form"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Private text field"]
                  [:paragraph "pdf test"]]]]]
              [[:heading {:spacing-before 20} "Events"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant created application 2000/1." nil nil nil]
                 [:phrase "2001-01-01 00:00" " " "Alice Applicant submitted the application for review." nil nil nil]
                 [:phrase "2003-01-01 00:00" " " "Developer requested a review from Carl Reviewer." nil "\nComment: please have a look" nil]
                 [:phrase "2003-01-01 00:00" " " "Developer requested a decision from David Decider." nil "\nComment: please decide" nil]
                 [:phrase "2003-01-01 00:00" " " "David Decider filed a decision for the application." "\nDavid Decider approved the application." "\nComment: I have decided" nil]]]]]
             (with-language :en
               (fn []
                 (with-fixed-time (time/date-time 2010)
                   (fn []
                     (#'pdf/render-application (applications/get-application-for-user "developer" application-id)))))))))
    (testing "decider should see complete application"
      (is (= [{}
              [[:heading {:spacing-before 20} "Hakemus 2000/1: "]
               [:paragraph "Tämä PDF luotu" " " "2010-01-01 00:00"]
               [:paragraph "Tila" [:phrase ": " "Haettu"]]
               [:heading {:spacing-before 20} "Hakijat"]
               [:paragraph "Hakija" ": " "Alice Applicant (alice) <alice@example.com>. Käyttöehdot hyväksytty: Kyllä"]
               []
               [:heading {:spacing-before 20} "Resurssit"]
               [:list [[:phrase "Resurssi" " (" "pdf-resource-ext" ")"]
                       [:phrase "Resurssi 2" " (" "pdf-resource-2-ext" ")"]]]]
              [[:heading {:spacing-before 20} "Käyttöehdot"] []]
              [[[:heading {:spacing-before 20} "Lomake"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Julkinen tekstikenttä"]
                  [:paragraph "pdf test"]]
                 [[:paragraph {:spacing-before 8, :style :bold} "Yksityinen tekstikenttä"]
                  [:paragraph "pdf test"]]]]
               [[:heading {:spacing-before 20} "Yksityinen lomake"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Yksityinen tekstikenttä"]
                  [:paragraph "pdf test"]]]]]
              [[:heading {:spacing-before 20} "Tapahtumat"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant loi hakemuksen 2000/1." nil nil nil]
                 [:phrase "2001-01-01 00:00" " " "Alice Applicant lähetti hakemuksen käsiteltäväksi." nil nil nil]
                 [:phrase "2003-01-01 00:00" " " "Developer pyysi katselmointia käyttäjältä Carl Reviewer." nil "\nKommentti: please have a look" nil]
                 [:phrase "2003-01-01 00:00" " " "Developer pyysi päätöstä käyttäjältä David Decider." nil "\nKommentti: please decide" nil]
                 [:phrase "2003-01-01 00:00" " " "David Decider teki päätöksen hakemukselle." "\nDavid Decider hyväksyi hakemuksen." "\nKommentti: I have decided" nil]]]]]
             (with-language :fi
               (fn []
                 (with-fixed-time (time/date-time 2010)
                   (fn []
                     (#'pdf/render-application (applications/get-application-for-user "david" application-id)))))))))
    (testing "reviewer should not see private fields"
      (is (= [{}
              [[:heading {:spacing-before 20} "Ansökan 2000/1: "]
               [:paragraph "Denna pdf skapades" " " "2010-01-01 00:00"]
               [:paragraph "Status" [:phrase ": " "Inlämnad"]]
               [:heading {:spacing-before 20} "Sökande"]
               [:paragraph "Sökande" ": " "Alice Applicant (alice) <alice@example.com>. Licenserna har accepterats: Ja"]
               []
               [:heading {:spacing-before 20} "Resurser"]
               [:list [[:phrase "Resurs" " (" "pdf-resource-ext" ")"]
                       [:phrase "Resurs 2" " (" "pdf-resource-2-ext" ")"]]]]
              [[:heading {:spacing-before 20} "Licenser"] []]
              [[[:heading {:spacing-before 20} "Blankett"]
                [[[:paragraph {:spacing-before 8, :style :bold} "Offentligt textfält"]
                  [:paragraph "pdf test"]]]]]
              [[:heading {:spacing-before 20} "Händelser"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant skapade ansökan 2000/1." nil nil nil]
                 [:phrase "2001-01-01 00:00" " " "Alice Applicant lämnade in ansökan." nil nil nil]
                 [:phrase "2003-01-01 00:00" " " "Developer begärde granskning av Carl Reviewer." nil "\nKommentar: please have a look" nil]
                 [:phrase "2003-01-01 00:00" " " "Developer begärde beslut från användare David Decider." nil "\nKommentar: please decide" nil]
                 [:phrase "2003-01-01 00:00" " " "David Decider behandlade ansökan." "\nDavid Decider godkände ansökan." "\nKommentar: I have decided" nil]]]]]
             (with-language :sv
               (fn []
                 (with-fixed-time (time/date-time 2010)
                   (fn []
                     (#'pdf/render-application (applications/get-application-for-user "carl" application-id)))))))))))

(deftest test-pdf-gold-standard
  (test-helpers/create-user! {:userid "alice" :name "Alice Applicant" :email "alice@example.com"})
  (test-helpers/create-user! {:userid "beth" :name "Beth Applicant" :email "beth@example.com"})
  (test-helpers/create-user! {:userid "david" :name "David Decider" :email "david@example.com"})
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
        form (test-helpers/create-form! {:form/internal-name  "Form"
                                         :form/external-title {:en "Form"
                                                               :fi  "Lomake"
                                                               :sv "Blankett"}
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
      (let [attachment-1 (:id (db/save-attachment! {:application application-id
                                                    :user handler
                                                    :filename "attachment.pdf"
                                                    :type "application/pdf"
                                                    :data (byte-array 0)}))
            attachment-2 (:id (db/save-attachment! {:application application-id
                                                    :user handler
                                                    :filename "picture.png"
                                                    :type "image/png"
                                                    :data (byte-array 0)}))]
        ;; two draft-saved events
        (test-helpers/fill-form! {:time (time/date-time 2000)
                                  :actor applicant
                                  :application-id application-id
                                  :field-value "pdf test"
                                  :attachment attachment-1
                                  :optional-fields true})
        (test-helpers/fill-form! {:time (time/date-time 2000)
                                  :actor applicant
                                  :application-id application-id
                                  :field-value "pdf test"
                                  :attachment (str attachment-1 "," attachment-2)
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
               [:paragraph "Applicant" ": " "Alice Applicant (alice) <alice@example.com>. Accepted terms of use: Yes"]
               [[:paragraph "Member" ": " "Beth Applicant (beth) <beth@example.com>. Accepted terms of use: No"]]
               [:heading pdf/heading-style "Resources"]
               [:list [[:phrase "Catalogue item" " (" "pdf-resource-ext" ")"]]]]
              [[:heading pdf/heading-style "Terms of use"]
               [[[:paragraph pdf/license-title-style "Google license"]
                 [:paragraph "http://google.com"]]
                [[:paragraph pdf/license-title-style "Text license"]
                 [:paragraph "Some text"]]]]
              [[[:heading pdf/heading-style "Form"]
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
                  [:paragraph "attachment.pdf, picture.png"]]
                 [[:paragraph pdf/field-style "Option list. Choose the first option to reveal a new field."]
                  [:paragraph "First option"]]
                 [[:paragraph pdf/field-style "Conditional field. Shown only if first option is selected above."]
                  [:paragraph "pdf test"]]
                 [[:paragraph pdf/field-style "Multi-select list"]
                  [:paragraph "First option"]]
                 [[:paragraph pdf/field-style "Table"]
                  [:paragraph
                   [:table {:header ["First" "Second"]}
                    ["pdf test" "pdf test"]
                    ["pdf test" "pdf test"]]]]
                 [[:paragraph pdf/label-field-style "The following field types can have a max length."]
                  [:paragraph ""]]
                 [[:paragraph pdf/field-style "Text field with max length"]
                  [:paragraph "pdf test"]]
                 [[:paragraph pdf/field-style "Text area with max length"]
                  [:paragraph "pdf test"]]
                 [[:paragraph pdf/field-style "Phone number"]
                  [:paragraph "+358451110000"]]
                 [[:paragraph pdf/field-style "IP address"]
                  [:paragraph "142.250.74.110"]]]]]
              [[:heading pdf/heading-style "Events"]
               [:list
                [[:phrase "2000-01-01 00:00" " " "Alice Applicant created application 2000/1." nil nil nil]
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
