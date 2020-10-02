(ns ^:integration rems.db.test-csv
  (:require [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.csv :as csv]
            [rems.text :as text])
  (:import [org.joda.time DateTime]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-print-to-csv
  (testing "with default settings"
    (is (= "col1,col2\r\nval1,val2\r\nval3,val4\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["val1" "val2"]
                                    ["val3" "val4"]]
                             :separator ","))))

  (testing "different separator"
    (is (= "col1;col2\r\nval1;val2\r\nval3;val4\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["val1" "val2"]
                                    ["val3" "val4"]]
                             :separator ";"))))

  (testing "quote strings"
    (is (= "\"col1\",\"col2\"\r\n\"val1\",\"val2\"\r\n\"val3\",\"val4\"\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["val1" "val2"]
                                    ["val3" "val4"]]
                             :separator ","
                             :quote-strings? true))))

  (testing "do not quote numeric values"
    (is (= "\"col1\",\"col2\"\r\n\"val1\",\"val2\"\r\n\"val3\",4\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["val1" "val2"]
                                    ["val3" 4]]
                             :separator ","
                             :quote-strings? true))))

  (testing "do not quote nil values"
    (is (= "\"col1\",\"col2\"\r\n\"val1\",\"val2\"\r\n,\"val4\"\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["val1" "val2"]
                                    [nil "val4"]]
                             :separator ","
                             :quote-strings? true))))

  (testing "escape quotes inside strings when strings are quoted"
    (is (= "\"col1\",\"col2\"\r\n\"\\\"so called\\\" value\",\"val2\"\r\n\"val3\",\"val4\"\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["\"so called\" value" "val2"]
                                    ["val3" "val4"]]
                             :separator ","
                             :quote-strings? true))))

  (testing "do not escape quotes inside strings when strings are not quoted"
    (is (= "col1,col2\r\n\"so called\" value,val2\r\nval3,val4\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["\"so called\" value" "val2"]
                                    ["val3" "val4"]]
                             :separator ","))))

  (testing "strip line returns"
    (is (= "col1,col2\r\nvalue  with line returns,val2\r\nval3,val  4\r\n"
           (csv/print-to-csv :column-names ["col1" "col2"]
                             :rows [["value\r\nwith\nline returns" "val2"]
                                    ["val3" "val\r\n4"]]
                             :separator ","
                             :strip-line-returns? true)))))

(def ^:private applicant "applicant")
(def ^:private test-time (DateTime. 1000))

;; TODO: This could be non-integration non-db test if the application was
;;       created from events.
(deftest test-applications-to-csv
  (test-helpers/create-user! {:eppn applicant :commonName "Alice Applicant" :mail "alice@applicant.com"})
  (let [form-id (test-helpers/create-form!
                 {:form/fields [{:field/title {:en "Application title"
                                               :fi "Hakemuksen otsikko"
                                               :sv "sv"}
                                 :field/optional false
                                 :field/type :description}
                                {:field/title {:en "Description"
                                               :fi "Kuvaus"
                                               :sv "sv"}
                                 :field/optional true
                                 :field/type :description}]})
        other-form-id (test-helpers/create-form!
                       {:form/fields [{:field/title {:en "SHOULD NOT BE VISIBLE"
                                                     :fi "SHOULD NOT BE VISIBLE"
                                                     :sv "sv"}
                                       :field/optional true
                                       :field/type :text}]})
        wf-id (test-helpers/create-workflow! {})
        cat-id (test-helpers/create-catalogue-item! {:title {:en "Test resource"
                                                             :fi "Testiresurssi"
                                                             :sv "sv"}
                                                     :form-id form-id
                                                     :workflow-id wf-id})
        other-cat-id (test-helpers/create-catalogue-item! {:title {:en "Other resource"
                                                                   :fi "Toinen resurssi"
                                                                   :sv "sv"}
                                                           :form-id other-form-id
                                                           :workflow-id wf-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id other-cat-id]
                                                  :actor applicant})
        external-id (:application/external-id (applications/get-application app-id))
        get-application #(applications/get-application app-id)]

    (test-helpers/fill-form! {:application-id app-id
                              :actor applicant
                              :field-value "test\nvalue"})

    (testing "form filled out"
      (is (= (str "\"Id\",\"External id\",\"Applicant\",\"Submitted\",\"State\",\"Resources\",\"Application title\",\"Description\"\r\n"
                  app-id ",\"" external-id "\",\"Alice Applicant\",,\"Draft\",\"Test resource, Other resource\",\"test value\",\"\"\r\n")
             (csv/applications-to-csv [(get-application)] form-id :en)))
      (testing "in finnish"
        (is (= (str "\"Tunniste\",\"Ulkoinen tunniste\",\"Hakija\",\"Lähetetty\",\"Tila\",\"Resurssit\",\"Hakemuksen otsikko\",\"Kuvaus\"\r\n"
                    app-id ",\"" external-id "\",\"Alice Applicant\",,\"Luonnos\",\"Testiresurssi, Toinen resurssi\",\"test value\",\"\"\r\n")
               (csv/applications-to-csv [(get-application)] form-id :fi)))))

    (test-helpers/accept-licenses! {:application-id app-id
                                    :actor applicant})

    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor applicant
                            :time test-time})

    (testing "submitted application"
      (is (= (str "\"Id\",\"External id\",\"Applicant\",\"Submitted\",\"State\",\"Resources\",\"Application title\",\"Description\"\r\n"
                  app-id ",\"" external-id "\",\"Alice Applicant\",\""
                  (text/localize-time test-time)
                  "\",\"Applied\",\"Test resource, Other resource\",\"test value\",\"\"\r\n")
             (csv/applications-to-csv [(get-application)] form-id :en))))))
