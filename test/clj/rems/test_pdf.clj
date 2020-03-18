(ns ^:integration rems.test-pdf
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.pdf :as pdf]
            [rems.text :refer [with-language]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-pdf-gold-standard
  (let [lic1 (test-data/create-license! {:license/type :link
                                         :license/title {:en "Google license"
                                                 :fi "Google-lisenssi"}
                                         :license/link {:en "http://google.com"
                                                        :fi "http://google.fi"}})
        lic2 (test-data/create-license! {:license/type :text
                                         :license/title {:en "Text license"
                                                 :fi "Tekstilisenssi"}
                                         :license/text {:en "Some text"
                                                        :fi "Tekstiä"}})
        ;; TODO attachment license
        resource (test-data/create-resource! {:resource-ext-id "pdf-resource-ext"
                                              :license-ids [lic1 lic2]})
        form (test-data/create-form! {:form/title "Form"
                                      :form/fields test-data/all-field-types-example})
        catalogue-item (test-data/create-catalogue-item! {:resource-id resource
                                                          :title {:en "Catalogue item"
                                                                  :fi "Katalogi-itemi"}
                                                          :form-id form})
        applicant "alice"
        application-id (test-data/create-draft! applicant [catalogue-item] "pdf test" (time/date-time 2000))
        handler "developer"]
    (testing "submit"
      (test-data/command! {:time (time/date-time 2001)
                           :application-id application-id
                           :type :application.command/submit
                           :actor applicant}))
    (testing "handler"
      (test-data/command! {:time (time/date-time 2002)
                           :application-id application-id
                           :type :application.command/approve
                           :comment "approved"
                           :actor handler}))
    (testing "pdf contents"
      (is (= '[{}
               [:heading "Application"]
               ([:paragraph "State" [:phrase ": " "Approved"]]
                [:heading "Applicant"]
                [:paragraph nil]
                [:paragraph "alice"]
                [:paragraph nil]
                [:heading "Resources"]
                [:list [:phrase "Catalogue item" " (" "pdf-resource-ext" ")"]]
                [:heading "Events"]
                [:table
                 {:header ["User" "Event" "Comment" "Time"]}
                 ;; TODO will break when timezone changes...
                 ["alice" "alice created a new application." "" "2000-01-01 02:00"]
                 ["alice" "alice saved the application as a draft." "" "2000-01-01 02:00"]
                 ["alice" "alice accepted the terms of use." "" "2000-01-01 02:00"]
                 ["alice" "alice submitted the application for review." "" "2001-01-01 02:00"]
                 ["developer" "Developer approved the application." "approved" "2002-01-01 02:00"]])
               ([:heading "This form demonstrates all possible field types. (This text itself is a label field.)"]
                [:paragraph "pdf test"]
                [:heading "Application title field"]
                [:paragraph "pdf test"]
                [:heading "Text field"]
                [:paragraph "pdf test"]
                [:heading "Text area"]
                [:paragraph "pdf test"]
                [:heading "Header"]
                [:paragraph "pdf test"]
                [:heading "Date field"]
                [:paragraph ""]
                [:heading "Email field"]
                [:paragraph ""]
                [:heading "Attachment"]
                [:paragraph ""]
                [:heading "Option list. Choose the first option to reveal a new field."]
                [:paragraph ""]
                [:heading "Conditional field. Shown only if first option is selected above."]
                [:paragraph "pdf test"]
                [:heading "Multi-select list"]
                [:paragraph ""]
                [:heading "The following field types can have a max length."]
                [:paragraph "pdf test"]
                [:heading "Text field with max length"]
                [:paragraph ""]
                [:heading "Text area with max length"]
                [:paragraph ""])
               ([:heading "Terms of use"]
                ([:paragraph "Google license"]
                 [:paragraph "Text license"]))]
             (with-language :en
               #(#'pdf/render-application (applications/get-application handler application-id))))))))
