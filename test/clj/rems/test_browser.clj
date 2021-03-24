(ns ^:browser rems.test-browser
  "REMS Browser tests.

  For the test database, you need to run these tests in the :test profile to get the right :database-url and :port.

  For development development tests, you can run against a running instance with:

  (rems.browser-test-util/init-driver! :chrome \"http://localhost:3000/\" :development)

  NB: Don't use etaoin directly but use it from the `browser-test-util` library that removes the need to pass the driver."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.rpl.specter :refer [select ALL]]
            [rems.api.services.form :as forms]
            [rems.api.services.organizations :as organizations]
            [rems.api.services.resource :as resources]
            [rems.api.services.workflow :as workflows]
            [rems.browser-test-util :as btu]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.users :as users]
            [rems.db.user-settings :as user-settings]
            [rems.standalone]
            [rems.testing-util :refer [with-user]]
            [rems.text :as text]))

(comment ; convenience for development testing
  (btu/init-driver! :chrome "http://localhost:3000/" :development))

(use-fixtures :each btu/fixture-refresh-driver)

(use-fixtures
  :once
  btu/ensure-empty-directories-fixture
  btu/test-dev-or-standalone-fixture
  btu/smoke-test
  btu/accessibility-report-fixture
  btu/fixture-init-driver)

;;; common functionality

(defn login-as [username]
  (btu/set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
  (btu/go (btu/get-server-url))
  (btu/screenshot "landing-page.png")
  (btu/scroll-and-click {:css ".login-btn"})
  (btu/screenshot "login-page.png")
  (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text username}])
  (btu/wait-visible :logout)
  (btu/screenshot "logged-in.png"))

(defn logout []
  (btu/scroll-and-click :logout)
  (btu/wait-visible {:css ".login-component"}))

(defn click-navigation-menu [link-text]
  (btu/scroll-and-click [:big-navbar {:tag :a :fn/text link-text}]))

(defn selected-navigation-menu? [link-text]
  (btu/has-class? [:big-navbar {:tag :a :fn/text link-text}] "active"))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
  (btu/wait-page-loaded)
  (btu/screenshot "catalogue-page.png"))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (btu/wait-visible {:tag :h1 :fn/text "Applications"})
  (btu/wait-page-loaded)
  (btu/screenshot "applications-page.png"))

(defn go-to-application [application-id]
  (btu/go (str (btu/get-server-url) "application/" application-id))
  (btu/wait-visible {:tag :h1 :fn/has-text "Application"})
  (btu/wait-page-loaded)
  (btu/screenshot "application-page.png"))

(defn click-administration-menu [link-text]
  (btu/scroll-and-click [:administration-menu {:tag :a :fn/text link-text}]))

(defn go-to-admin [link-text]
  (when-not (selected-navigation-menu? "Administration")
    (click-navigation-menu "Administration")
    (btu/wait-page-loaded))
  (click-administration-menu link-text)
  (btu/wait-visible {:tag :h1 :fn/text link-text})
  (btu/wait-page-loaded)
  (btu/screenshot (str "administration-page-" (str/replace link-text " " "-") ".png")))

(defn change-language [language]
  (btu/scroll-and-click [{:css ".language-switcher"} {:fn/text (.toUpperCase (name language))}]))

;;; catalogue page

(defn add-to-cart [resource-name]
  (btu/scroll-and-click [{:css "table.catalogue"}
                         {:fn/text resource-name}
                         {:xpath "./ancestor::tr"}
                         {:css ".add-to-cart"}]))

(defn apply-for-resource [resource-name]
  (btu/scroll-and-click [{:css "table.cart"}
                         {:fn/text resource-name}
                         {:xpath "./ancestor::tr"}
                         {:css ".apply-for-catalogue-items"}])
  (btu/wait-visible {:tag :h1 :fn/has-text "Application"})
  (btu/wait-page-loaded)
  (btu/screenshot "application-page.png"))

;;; application page

(defn slurp-fields [selector]
  (->> (for [row (btu/query-all [selector {:fn/has-class :form-group}])
             :when (btu/visible-el? row)
             :let [k (btu/get-element-text-el (btu/child row {:tag :label}))
                   v (btu/first-value-of-el row [{:css ".form-control"}
                                                 {:css ".dropdown-container"}
                                                 {:css ".list-group"}])]]
         [k v])
       (into {})))

(defn slurp-rows [& selectors]
  (for [row (btu/query-all (vec (concat selectors [{:css "tr"}])))]
    (->> (for [td (btu/children row {:css "td"})
               :let [k (str/trim (btu/get-element-attr-el td "class"))
                     v (btu/first-value-of-el td)]]
           [k v])
         (into {}))))

(defn find-rows [table-selectors child-selector]
  (for [row (btu/query-all (vec (concat table-selectors [{:css "tr"}])))
        :when (seq (btu/children row child-selector))]
    row))

(defn click-row-action [table-selectors child-selector button-selector]
  (let [rows (seq (find-rows table-selectors child-selector))]
    (is (= 1 (count rows)))
    (btu/scroll-and-click-el
     (btu/child (first rows)
                button-selector))))

(defn- select-button-by-label [label]
  {:css ".btn" :fn/text label})

(comment
  (find-rows [:licenses]
             {:fn/text (str (btu/context-get :license-name) " EN")}))

(defn fill-form-field
  "Fills a form field named by `label` with `text`.

  Optionally give `:index` when several items match. It starts from 1."
  [label text & [opts]]
  (assert (> (:index opts 1) 0) "indexing starts at 1") ; xpath uses 1, let's keep the convention though we don't use xpath here because it will likely not work

  (let [el (nth (btu/query-all [{:css ".fields"}
                                {:tag :label :fn/has-text label}])
                (dec (:index opts 1)))
        id (btu/get-element-attr-el el :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (btu/fill-human {:id id} text)))

(defn set-date [id date]
  ;; XXX: The date format depends on operating system settings and is unaffected by browser locale,
  ;;      so we cannot reliably know the date format to type into the date field and anyways WebDriver
  ;;      support for date fields seems buggy. Changing the field with JavaScript is more reliable.
  (btu/js-execute
   ;; XXX: React workaround for dispatchEvent, see https://github.com/facebook/react/issues/10135
   "
   function setNativeValue(element, value) {
       const { set: valueSetter } = Object.getOwnPropertyDescriptor(element, 'value') || {}
       const prototype = Object.getPrototypeOf(element)
       const { set: prototypeValueSetter } = Object.getOwnPropertyDescriptor(prototype, 'value') || {}

       if (prototypeValueSetter && valueSetter !== prototypeValueSetter) {
           prototypeValueSetter.call(element, value)
       } else if (valueSetter) {
           valueSetter.call(element, value)
       } else {
           throw new Error('The given element does not have a value setter')
       }
   }
   var field = document.getElementById(arguments[0])
   setNativeValue(field, arguments[1])
   field.dispatchEvent(new Event('change', {bubbles: true}))
   "
   id date))

(defn set-date-for-label [label date]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/text label}]
                                 :for)]
    (set-date id date)))

(defn select-option [label option]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/has-text label}]
                                 :for)]
    (btu/fill {:id id} (str option "\n"))))

;; TODO  see if the select-option could be combined
(defn select-option*
  "Version of `select-option` that does not limit to .fields."
  [label option]
  (let [id (btu/get-element-attr [{:tag :label :fn/has-text label}]
                                 :for)]
    (btu/fill {:id id} (str option "\n"))))

(defn accept-licenses []
  (btu/scroll-and-click :accept-licenses-button)
  (btu/wait-visible :has-accepted-licenses))

(defn send-application []
  (btu/scroll-and-click :submit)
  (btu/wait-visible :status-success)
  (btu/wait-has-class :apply-phase "completed"))

(defn get-application-id []
  (last (str/split (btu/get-url) #"/")))

(defn get-attachments
  ([]
   (get-attachments {:css "a.attachment-link"}))
  ([selector]
   (mapv (partial btu/get-element-text-el) (btu/query-all selector))))

;; applications page

(defn get-application-summary [application-id]
  (let [row (btu/query [{:css "table.my-applications"}
                        {:tag :tr :data-row application-id}])]
    {:id application-id
     :description (btu/get-element-text-el (btu/child row {:css ".description"}))
     :resource (btu/get-element-text-el (btu/child row {:css ".resource"}))
     :state (btu/get-element-text-el (btu/child row {:css ".state"}))}))

(defn- get-application-from-api [application-id & [userid]]
  (:body
   (http/get (str (btu/get-server-url) "/api/applications/" application-id)
             {:as :json
              :headers {"x-rems-api-key" "42"
                        "x-rems-user-id" (or userid "handler")}})))

;;; tests

(deftest test-new-application
  (btu/with-postmortem
    (login-as "alice")
    (btu/gather-axe-results)

    (testing "create application"
      (go-to-catalogue)
      (add-to-cart "Default workflow")
      (btu/gather-axe-results)
      (apply-for-resource "Default workflow")
      (btu/gather-axe-results)

      (btu/context-assoc! :application-id (get-application-id))

      (let [application (:body
                         (http/get (str (btu/get-server-url) "/api/applications/" (btu/context-get :application-id))
                                   {:as :json
                                    :headers {"x-rems-api-key" "42"
                                              "x-rems-user-id" "alice"}}))
            form-id (get-in application [:application/forms 0 :form/id])
            description-field-id (get-in application [:application/forms 0 :form/fields 1 :field/id])
            description-field-selector (keyword (str "form-" form-id "-field-" description-field-id))
            attachment-field (get-in application [:application/forms 0 :form/fields 7])
            attachment-field-id (str "form-" form-id "-field-" (:field/id attachment-field))
            attachment-field-upload-selector (keyword (str "upload-" attachment-field-id "-input"))
            conditional-field (get-in application [:application/forms 0 :form/fields 9])
            conditional-field-id (str "form-" form-id "-field-" (:field/id conditional-field))
            table-field (get-in application [:application/forms 0 :form/fields 11])
            table-field-id (str "form-" form-id "-field-" (:field/id table-field))]
        ;; sanity checks:
        (is (= "attachment" (:field/type attachment-field)))
        (is (= "table" (:field/type table-field)))
        (is (:field/visibility conditional-field))

        (fill-form-field "Application title field" "Test name")
        (fill-form-field "Text field" "Test")
        (fill-form-field "Text area" "Test2")
        (set-date-for-label "Date field" "2050-01-02")

        (fill-form-field "Email field" "user@example.com")
        (fill-form-field "Phone number" "+358450000100")
        (fill-form-field "IP address" "192.168.0.254")

        (testing "upload three attachments, then remove one"
          (btu/upload-file attachment-field-upload-selector "test-data/test.txt")
          (btu/wait-predicate #(= ["test.txt"] (get-attachments)))
          (btu/upload-file attachment-field-upload-selector "test-data/test-fi.txt")
          (btu/wait-predicate #(= ["test.txt" "test-fi.txt"] (get-attachments)))
          (btu/upload-file attachment-field-upload-selector "test-data/test-sv.txt")
          (btu/wait-predicate #(= ["test.txt" "test-fi.txt" "test-sv.txt"] (get-attachments)))
          (btu/scroll-and-click-el (last (btu/query-all {:css (str "button.remove-attachment-" attachment-field-id)}))))

        (is (not (btu/field-visible? "Conditional field"))
            "Conditional field is not visible before selecting option")

        (select-option "Option list" "First option")
        (btu/wait-predicate #(btu/field-visible? "Conditional field"))
        (fill-form-field "Conditional field" "Conditional")

        ;; check that answers to conditional fields are retained even if they're temporarily invisible
        (select-option "Option list" "Second option")
        (btu/wait-predicate #(not (btu/field-visible? "Conditional field")))
        (select-option "Option list" "First option")
        (btu/wait-predicate #(btu/field-visible? "Conditional field"))
        (is (= "Conditional" (btu/value-of (keyword conditional-field-id))))

        ;; pick two options for the multi-select field:
        (btu/check-box "Option2")
        (btu/check-box "Option3")
        ;; fill in two rows for the table
        (btu/scroll-and-click (keyword (str table-field-id "-add-row")))
        (btu/wait-visible (keyword (str table-field-id "-row0-col1")))
        (btu/scroll-and-click (keyword (str table-field-id "-add-row")))
        (btu/wait-visible (keyword (str table-field-id "-row1-col1")))
        (btu/fill-human (keyword (str table-field-id "-row0-col1")) "a")
        (btu/fill-human (keyword (str table-field-id "-row0-col2")) "b")
        (btu/fill-human (keyword (str table-field-id "-row1-col1")) "c")
        (btu/fill-human (keyword (str table-field-id "-row1-col2")) "d")

        ;; leave "Text field with max length" empty
        ;; leave "Text are with max length" empty

        (accept-licenses)
        (btu/gather-axe-results)

        (send-application)
        (btu/gather-axe-results)

        (is (= "Applied" (btu/get-element-text :application-state)))

        (testing "check a field answer"
          (is (= "Test name" (btu/get-element-text description-field-selector))))

        (testing "check that table field values are visible"
          (is (= "a" (btu/value-of (keyword (str table-field-id "-row0-col1")))))
          (is (= "b" (btu/value-of (keyword (str table-field-id "-row0-col2")))))
          (is (= "c" (btu/value-of (keyword (str table-field-id "-row1-col1")))))
          (is (= "d" (btu/value-of (keyword (str table-field-id "-row1-col2"))))))

        (testing "fetch application from API"
          (let [application (get-application-from-api (btu/context-get :application-id))]
            (btu/context-assoc! :attachment-ids (mapv :attachment/id (:application/attachments application)))

            (testing "see application on applications page"
              (go-to-applications)
              (btu/gather-axe-results)

              (is (= {:id (btu/context-get :application-id)
                      :resource "Default workflow"
                      :state "Applied"
                      :description "Test name"}
                     (get-application-summary (btu/context-get :application-id)))))

            (testing "attachments"
              (is (= [{:attachment/id (first (btu/context-get :attachment-ids))
                       :attachment/filename "test.txt"
                       :attachment/type "text/plain"}
                      {:attachment/id (second (btu/context-get :attachment-ids))
                       :attachment/filename "test-fi.txt"
                       :attachment/type "text/plain"}]
                     (:application/attachments application))))
            (testing "applicant information"
              (is (= "alice" (get-in application [:application/applicant :userid])))
              (is (= (set (map :license/id (:application/licenses application)))
                     (set (get-in application [:application/accepted-licenses :alice])))))
            (testing "form fields"
              (is (= "Test name" (:application/description application)))
              (is (= [["label" ""]
                      ["description" "Test name"]
                      ["text" "Test"]
                      ["texta" "Test2"]
                      ["header" ""]
                      ["date" "2050-01-02"]
                      ["email" "user@example.com"]
                      ["attachment" (str/join "," (btu/context-get :attachment-ids))]
                      ["option" "Option1"]
                      ["text" "Conditional"]
                      ["multiselect" "Option2 Option3"]
                      ["table" [[{:column "col1", :value "a"} {:column "col2", :value "b"}]
                                [{:column "col1", :value "c"} {:column "col2", :value "d"}]]]
                      ["label" ""]
                      ["text" ""]
                      ["texta" ""]
                      ["phone-number" "+358450000100"]
                      ["ip-address" "192.168.0.254"]]
                     (for [field (select [:application/forms ALL :form/fields ALL] application)]
                       ;; TODO could test other fields here too, e.g. title
                       [(:field/type field)
                        (:field/value field)]))))
            (testing "after navigating to the application view again"
              (btu/scroll-and-click [{:css "table.my-applications"}
                                     {:tag :tr :data-row (btu/context-get :application-id)}
                                     {:css ".btn-primary"}])
              (btu/wait-visible {:tag :h1 :fn/has-text "Application"})
              (btu/wait-page-loaded)
              (btu/gather-axe-results)
              (testing "check a field answer"
                (is (= "Test name" (btu/get-element-text description-field-selector)))))))))))

(deftest test-applicant-member-invite-action
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-applicant-member-invite-action")))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-application (btu/context-get :application-id))

    (testing "invite member"
      (is (not (btu/visible? [:actions-invite-member {:fn/has-text "Invite member"}])))
      (btu/scroll-and-click :invite-member-action-button)
      (btu/wait-visible [:actions-invite-member {:fn/has-text "Invite member"}])
      (btu/fill-human [:actions-invite-member :name-invite-member] "John Smith")
      (btu/fill-human [:actions-invite-member :email-invite-member] "john.smith@generic.name")
      (btu/scroll-and-click :invite-member)
      (btu/wait-invisible [:actions-invite-member {:fn/has-text "Invite member"}])
      (btu/scroll-and-click :invite0-info-collapse-more-link)
      (btu/wait-visible :invite0-info-collapse)

      (is (= {"Name" "John Smith"
              "Email (from identity provider)" "john.smith@generic.name"}
             (slurp-fields :invite0-info)))
      (is (string? (-> (btu/context-get :application-id)
                       applications/get-application-internal
                       :application/invitation-tokens
                       keys
                       first)))
      (is (= {:event/actor "alice"
              :application/member {:name "John Smith"
                                   :email "john.smith@generic.name"}}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/invitation-tokens
                 vals
                 first)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant invited John Smith to the application."})))

    (testing "uninvite member"
      (is (not (btu/visible? :actions-invite0-remove-member-form)))
      (btu/scroll-and-click :invite0-remove-member-form-action-button)
      (btu/wait-visible :actions-invite0-remove-member-form)
      (btu/fill-human :comment-invite0-remove-member-comment "sorry but no")
      (btu/scroll-and-click :invite0-remove-member-submit)
      (btu/wait-visible [{:css ".alert-success" :fn/has-text "Remove member: Success"}])
      (btu/wait-invisible :actions-invite0-remove-member-form)
      (btu/wait-invisible :invite0-info)

      (is (empty? (-> (btu/context-get :application-id)
                      applications/get-application-internal
                      :application/invitation-tokens)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant removed John Smith from the application."}))
      (is (btu/visible? {:css "div.event-comment" :fn/text "sorry but no"})))))

(deftest test-applicant-member-remove-action
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {:handlers ["handler"]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)
                                                                            :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-applicant-member-remove-action"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"})
    (test-helpers/create-user! {:eppn "ionna" :commonName "Ionna Insprucker" :mail "ionna@ins.mail"})
    (test-helpers/create-user! {:eppn "jade" :commonName "Jade Jenner" :mail "jade80@mail.name"})
    (test-helpers/create-user! {:eppn "kayla" :commonName "Kayla Kale" :mail "kale@is.good"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "ionna"}
                            :actor "handler"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "jade"}
                            :actor "handler"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "kayla"}
                            :actor "handler"}))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-application (btu/context-get :application-id))

    (testing "remove second member jade"
      (is (not (btu/visible? :actions-member1-remove-member-form)))
      (btu/scroll-and-click :member1-remove-member-form-action-button)
      (btu/wait-visible :actions-member1-remove-member-form)
      (btu/fill-human :comment-member1-remove-member-comment "not in research group anymore")
      (btu/scroll-and-click :member1-remove-member-submit)
      (btu/wait-visible [{:css ".alert-success" :fn/has-text "Remove member: Success"}])
      (btu/wait-invisible :actions-member1-remove-member-form)
      (btu/wait-invisible :member2-info) ; last element is removed from DOM, remaining updated

      (is (= #{{:userid "ionna" :name "Ionna Insprucker" :email "ionna@ins.mail"}
               {:userid "kayla" :name "Kayla Kale" :email "kale@is.good"}}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/members)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant removed Jade Jenner from the application."}))
      (is (btu/visible? {:css "div.event-comment" :fn/text "not in research group anymore"})))))

(deftest test-handling
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title    {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type     :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-handling"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"}))
  (btu/with-postmortem
    (login-as "developer")
    (testing "handler should see todos on logging in"
      (btu/wait-visible :todo-applications))
    (testing "handler should see description of application"
      (btu/wait-visible {:class :application-description :fn/text "test-handling"}))
    (let [app-button {:tag :a :href (str "/application/" (btu/context-get :application-id))}]
      (testing "handler should see view button for application"
        (btu/wait-visible app-button))
      (btu/scroll-and-click app-button))
    (testing "handler should see application after clicking on View"
      (btu/wait-visible {:tag :h1 :fn/has-text "test-handling"}))
    (testing "handler should see the applicant info"
      (btu/scroll-and-click :applicant-info-collapse-more-link)
      (is (= {"Name" "Alice Applicant"
              "Accepted terms of use" true
              "Username" "alice"
              "Email (from identity provider)" "alice@example.com"
              "Organization" "Default"
              "Nickname" "In Wonderland"
              "Applicant researcher status" true}
             (slurp-fields :applicant-info))))
    (testing "open the approve form"
      (btu/scroll-and-click :approve-reject-action-button))
    (testing "add a comment and two attachments"
      (btu/wait-visible :comment-approve-reject)
      (btu/fill-human :comment-approve-reject "this is a comment")
      (btu/upload-file :upload-approve-reject-input "test-data/test.txt")
      (btu/wait-visible [{:css "a.attachment-link"}])
      (btu/upload-file :upload-approve-reject-input "test-data/test-fi.txt")
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt"]
                              (get-attachments))))
    (testing "add and remove a third attachment"
      (btu/upload-file :upload-approve-reject-input "resources/public/img/rems_logo_en.png")
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt" "rems_logo_en.png"]
                              (get-attachments)))
      (let [buttons (btu/query-all {:css "button.remove-attachment-approve-reject"})]
        (btu/click-el (last buttons)))
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt"]
                              (get-attachments))))
    (testing "approve"
      (btu/scroll-and-click :approve)
      (btu/wait-predicate #(= "Approved" (btu/get-element-text :application-state))))
    (testing "event visible in eventlog"
      (is (btu/visible? {:css "div.event-description b" :fn/text "Developer approved the application."})))
    (testing "attachments visible in eventlog"
      (is (= ["test.txt" "test-fi.txt"]
             (get-attachments {:css "div.event a.attachment-link"}))))
    (testing "event via api"
      ;; Note the absence of :entitlement/end, c.f. test-approve-with-end-date
      (is (= {:application/id (btu/context-get :application-id)
              :event/type "application.event/approved"
              :application/comment "this is a comment"
              :event/actor "developer"}
             (-> (get-application-from-api (btu/context-get :application-id) "developer")
                 :application/events
                 last
                 (dissoc :event/id :event/time :event/attachments :event/actor-attributes)))))))

(deftest test-invite-decider
  (testing "create test data"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-invite-decider"))
    (test-helpers/submit-application (btu/context-get :application-id) "alice")
    (test-helpers/create-user! {:eppn "new-reviewer" :commonName "New Reviewer"}))
  (btu/with-postmortem
    (testing "handler invites reviewer"
      (login-as "developer")
      (go-to-application (btu/context-get :application-id))
      (btu/wait-visible {:tag :h1 :fn/has-text "test-invite-decider"})

      (btu/scroll-and-click :request-decision-dropdown)
      (btu/wait-visible :invite-decider-action-button)
      (btu/scroll-and-click :invite-decider-action-button)

      (btu/wait-visible :name-invite-decider)
      (btu/fill-human :name-invite-decider "anybody will do")
      (btu/fill-human :email-invite-decider "user@example.com")
      (btu/scroll-and-click :invite-decider)
      (btu/wait-visible {:css ".alert-success"})
      (logout))
    (testing "get invite token"
      (let [[token invitation] (-> (btu/context-get :application-id)
                                   applications/get-application-internal
                                   :application/invitation-tokens
                                   first)]
        (is (string? token))
        (is (= {:application/decider {:name "anybody will do" :email "user@example.com"}
                :event/actor "developer"}
               invitation))
        (btu/context-assoc! :token token)))
    (testing "accept invitation"
      (btu/go (str (btu/get-server-url) "application/accept-invitation/" (btu/context-get :token)))
      (btu/wait-visible {:css ".login-btn"})
      (btu/scroll-and-click {:css ".login-btn"})
      (btu/wait-visible [{:css ".users"} {:tag :a :fn/text "new-reviewer"}])
      (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text "new-reviewer"}])
      (btu/wait-page-loaded)
      (btu/wait-visible {:tag :h1 :fn/has-text "test-invite-decider"}))
    (testing "check decider-joined event"
      (is (= {:event/type :application.event/decider-joined
              :event/actor "new-reviewer"}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/events
                 last
                 (select-keys [:event/actor :event/type])))))
    (testing "submit decision"
      (btu/scroll-and-click :decide-action-button)
      (btu/wait-visible :comment-decide)
      (btu/fill-human :comment-decide "ok")
      (btu/scroll-and-click :decide-approve)
      (btu/wait-page-loaded)
      (btu/wait-visible {:css ".alert-success"}))
    (testing "check decision event"
      (is (= {:application/decision :approved
              :application/comment "ok"
              :event/actor "new-reviewer"
              :event/type :application.event/decided}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/events
                 last
                 (select-keys [:application/decision :application/comment :event/actor :event/type])))))))

(deftest test-approve-with-end-date
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-approve-with-end-date"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"}))
  (btu/with-postmortem
    (login-as "developer")
    (btu/go (str (btu/get-server-url) "application/" (btu/context-get :application-id)))
    (btu/wait-visible {:tag :h1 :fn/has-text "test-approve-with-end-date"})
    (testing "approve"
      (btu/scroll-and-click :approve-reject-action-button)
      (btu/wait-visible :comment-approve-reject)
      (btu/fill-human :comment-approve-reject "this is a comment")
      (set-date :approve-end "2100-05-06")
      (btu/scroll-and-click :approve)
      (btu/wait-predicate #(= "Approved" (btu/get-element-text :application-state))))
    (testing "event visible in eventlog"
      (is (btu/visible? {:css "div.event-description b" :fn/text "Developer approved the application. Access rights end 2100-05-06"})))
    (testing "event via api"
      (is (= {:application/id (btu/context-get :application-id)
              :event/type "application.event/approved"
              :application/comment "this is a comment"
              :entitlement/end "2100-05-06T23:59:59.000Z"
              :event/actor "developer"}
             (-> (get-application-from-api (btu/context-get :application-id) "developer")
                 :application/events
                 last
                 (dissoc :event/id :event/time :event/attachments :event/actor-attributes)))))))

(deftest test-guide-page
  (btu/with-postmortem
    (btu/go (str (btu/get-server-url) "guide"))
    (btu/wait-visible {:tag :h1 :fn/text "Component Guide"})
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (btu/query-all {:class :example}))))))

(deftest test-language-change
  (btu/with-postmortem
    (testing "default language is English"
      (btu/go (btu/get-server-url))
      (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
      (login-as "alice")
      (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
      (btu/wait-page-loaded))

    (testing "changing language while logged out"
      (logout)
      (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
      (change-language :fi)
      (btu/wait-visible {:tag :h1 :fn/text "Tervetuloa REMSiin"}))

    (testing "changed language must persist after login"
      (login-as "alice")
      (btu/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"})
      (btu/wait-page-loaded))

    (testing "wait for language change to show in the db"
      (btu/wait-predicate #(= :fi (:language (user-settings/get-user-settings "alice")))))

    (testing "changed language must have been saved for user"
      (logout)
      (change-language :en)
      (btu/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
      (btu/delete-cookies)
      (login-as "alice")
      (btu/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"}))

    (testing "changing language while logged in"
      (change-language :en)
      (btu/wait-visible {:tag :h1 :fn/text "Catalogue"}))

    (user-settings/delete-user-settings! "alice") ; clear language settings
    (is true))) ; avoid no assertions warning

(defn create-license []
  (testing "create license"
    (btu/with-postmortem
      (go-to-admin "Licenses")
      (btu/scroll-and-click :create-license)
      (btu/wait-visible {:tag :h1 :fn/text "Create license"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (btu/scroll-and-click :licensetype-link)
      (fill-form-field "License name" (str (btu/context-get :license-name) " EN") {:index 1})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
      (fill-form-field "License name" (str (btu/context-get :license-name) " FI") {:index 2})
      (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
      (fill-form-field "License name" (str (btu/context-get :license-name) " SV") {:index 3})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 3})
      (btu/screenshot "about-to-create-license.png")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "License"})
      (btu/wait-page-loaded)
      (btu/screenshot "created-license.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title (EN)" (str (btu/context-get :license-name) " EN")
              "Title (FI)" (str (btu/context-get :license-name) " FI")
              "Title (SV)" (str (btu/context-get :license-name) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" true}
             (slurp-fields :license)))
      (go-to-admin "Licenses")
      (btu/wait-visible {:tag :h1 :fn/text "Licenses"})
      (is (some #{{"organization" "NBN"
                   "title" (str (btu/context-get :license-name) " EN")
                   "type" "link"
                   "active" true
                   "commands" "ViewDisableArchive"}}
                (slurp-rows :licenses)))
      (click-row-action [:licenses]
                        {:fn/text (str (btu/context-get :license-name) " EN")}
                        (select-button-by-label "View"))
      (btu/wait-visible :license)
      (is (= {"Organization" "NBN"
              "Title (EN)" (str (btu/context-get :license-name) " EN")
              "Title (FI)" (str (btu/context-get :license-name) " FI")
              "Title (SV)" (str (btu/context-get :license-name) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" true}
             (slurp-fields :license))))))

(defn create-resource []
  (testing "create resource"
    (btu/with-postmortem
      (go-to-admin "Resources")
      (btu/scroll-and-click :create-resource)
      (btu/wait-visible {:tag :h1 :fn/text "Create resource"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Resource identifier" (btu/context-get :resid))
      (select-option "License" (str (btu/context-get :license-name) " EN"))
      (btu/screenshot "about-to-create-resource.png")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Resource"})
      (btu/wait-page-loaded)
      (btu/screenshot "created-resource.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Resource" (btu/context-get :resid)
              "Active" true}
             (slurp-fields :resource)))
      (is (= (str "License \"" (btu/context-get :license-name) " EN\"")
             (btu/get-element-text [:licenses {:class :license-title}])))
      (go-to-admin "Resources")
      (is (some #(= (btu/context-get :resid) (get % "title"))
                (slurp-rows :resources))))))


(defn create-form []
  (testing "create form"
    (btu/with-postmortem
      (go-to-admin "Forms")
      (btu/scroll-and-click :create-form)
      (btu/wait-visible {:tag :h1 :fn/text "Create form"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Name" (btu/context-get :form-name))
      (fill-form-field "EN" (str (btu/context-get :form-name) " EN"))
      (fill-form-field "FI" (str (btu/context-get :form-name) " FI"))
      (fill-form-field "SV" (str (btu/context-get :form-name) " SV"))
      ;; TODO: create fields
      (btu/screenshot "about-to-create-form.png")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Form"})
      (btu/wait-page-loaded)
      (btu/screenshot "created-form.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Name" (btu/context-get :form-name)
              "Title (EN)" (str (btu/context-get :form-name) " EN")
              "Title (FI)" (str (btu/context-get :form-name) " FI")
              "Title (SV)" (str (btu/context-get :form-name) " SV")
              "Active" true}
             (slurp-fields :form)))
      (go-to-admin "Forms")
      (is (some #(= (btu/context-get :form-name) (get % "internal-name"))
                (slurp-rows :forms))))))

(defn create-workflow []
  (testing "create workflow"
    (btu/with-postmortem
      (go-to-admin "Workflows")
      (btu/scroll-and-click :create-workflow)
      (btu/wait-visible {:tag :h1 :fn/text "Create workflow"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :workflow-name))
      ;; Default workflow is already checked
      (select-option "Handlers" "handler")
      ;; No form
      (btu/screenshot "about-to-create-workflow.png")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Workflow"})
      (btu/wait-page-loaded)
      (btu/screenshot "created-workflow.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :workflow-name)
              "Type" "Default workflow"
              "Handlers" "Hannah Handler (handler@example.com)"
              "Forms" ""
              "Active" true}
             (slurp-fields :workflow)))
      (go-to-admin "Workflows")
      (is (some #(= (btu/context-get :workflow-name) (get % "title"))
                (slurp-rows :workflows))))))

(defn create-catalogue-item []
  (testing "create catalogue item"
    (btu/with-postmortem
      (go-to-admin "Catalogue items")
      (btu/scroll-and-click :create-catalogue-item)
      (btu/wait-visible {:tag :h1 :fn/text "Create catalogue item"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :catalogue-item-name) {:index 1})
      (fill-form-field "Title" (str (btu/context-get :catalogue-item-name) " FI") {:index 2})
      (fill-form-field "Title" (str (btu/context-get :catalogue-item-name) " SV") {:index 3})
      (select-option "Workflow" (btu/context-get :workflow-name))
      (select-option "Resource" (btu/context-get :resid))
      (select-option "Form" (btu/context-get :form-name))
      (btu/screenshot "about-to-create-catalogue-item.png")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Catalogue item"})
      (btu/wait-page-loaded)
      (btu/screenshot "created-catalogue-item.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title (EN)" (btu/context-get :catalogue-item-name)
              "Title (FI)" (str (btu/context-get :catalogue-item-name) " FI")
              "Title (SV)" (str (btu/context-get :catalogue-item-name) " SV")
              "More info (EN)" ""
              "More info (FI)" ""
              "More info (SV)" ""
              "Workflow" (btu/context-get :workflow-name)
              "Resource" (btu/context-get :resid)
              "Form" (btu/context-get :form-name)
              "Active" false
              "End" ""}
             (dissoc (slurp-fields :catalogue-item)
                     "Start")))
      (go-to-admin "Catalogue items")
      (is (some #(= {"workflow" (btu/context-get :workflow-name)
                     "resource" (btu/context-get :resid)
                     "form" (btu/context-get :form-name)
                     "name" (btu/context-get :catalogue-item-name)}
                    (select-keys % ["resource" "workflow" "form" "name"]))
                (slurp-rows :catalogue))))))

(defn enable-catalogue-item [item-name]
  (go-to-admin "Catalogue items")
  (btu/wait-page-loaded)
  ;; incidentally test search while we're at it
  (btu/fill-human :catalogue-search item-name)
  (btu/wait-page-loaded)
  (btu/screenshot "about-to-enable-catalogue-item.png")
  (btu/scroll-and-click {:tag :button :fn/text "Enable"})
  (btu/wait-page-loaded)
  (btu/screenshot "enabled-catalogue-item.png")
  (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))

(deftest test-create-catalogue-item
  (btu/with-postmortem
    (testing "create objects"
      (login-as "owner")
      (btu/context-assoc! :license-name (str "Browser Test License " (btu/get-seed))
                          :resid (str "browser.testing.resource/" (btu/get-seed))
                          :form-name (str "Browser Test Form " (btu/get-seed))
                          :workflow-name (str "Browser Test Workflow " (btu/get-seed))
                          :catalogue-item-name (str "Browser Test Catalogue Item " (btu/get-seed)))
      (create-license)
      (create-resource)
      (create-form)
      (create-workflow)
      (create-catalogue-item))
    (testing "check that catalogue item is not visible before enabling"
      (go-to-catalogue)
      (is (not (btu/visible? {:fn/text (btu/context-get :catalogue-item-name)}))))
    (testing "enable catalogue item"
      (enable-catalogue-item (btu/context-get :catalogue-item-name)))
    (testing "check that catalogue item is visible for applicants"
      (logout)
      (login-as "alice")
      (go-to-catalogue)
      (is (btu/visible? {:fn/text (btu/context-get :catalogue-item-name)})))))

(deftest test-edit-catalogue-item
  (btu/with-postmortem
    (btu/context-assoc! :organization-id (str "organization " (btu/get-seed)))
    (btu/context-assoc! :organization-name (str "Organization " (btu/get-seed)))
    (btu/context-assoc! :organization (test-helpers/create-organization! {:organization/id (btu/context-get :organization-id)
                                                                          :organization/short-name {:en "ORGen" :fi "ORGfi" :sv "ORGsv"}
                                                                          :organization/name {:en (str (btu/context-get :organization-name) " en")
                                                                                              :fi (str (btu/context-get :organization-name) " fi")
                                                                                              :sv (str (btu/context-get :organization-name) " sv")}}))
    (btu/context-assoc! :workflow (test-helpers/create-workflow! {:title "test-edit-catalogue-item workflow"
                                                                  :type :workflow/default
                                                                  :organization {:organization/id (btu/context-get :organization-id)}
                                                                  :handlers ["handler"]}))
    (btu/context-assoc! :resource (test-helpers/create-resource! {:resource-ext-id "test-edit-catalogue-item resource"
                                                                  :organization {:organization/id (btu/context-get :organization-id)}}))
    (btu/context-assoc! :form (test-helpers/create-form! {:form/internal-name "test-edit-catalogue-item form"
                                                          :form/external-title {:en "Test Edit Catalogue Item Form EN"
                                                                                :fi "Test Edit Catalogue Item Form FI"
                                                                                :sv "Test Edit Catalogue Item Form SV"}
                                                          :form/fields []
                                                          :form/organization {:organization/id (btu/context-get :organization-id)}}))
    (btu/context-assoc! :catalogue-item (test-helpers/create-catalogue-item! {:title {:en "test-edit-catalogue-item EN"
                                                                                      :fi "test-edit-catalogue-item FI"
                                                                                      :sv "test-edit-catalogue-item SV"}
                                                                              :resource-id (btu/context-get :resource)
                                                                              :form-id (btu/context-get :form)
                                                                              :workflow-id (btu/context-get :workflow)
                                                                              :organization {:organization/id (btu/context-get :organization-id)}}))
    (login-as "owner")
    (btu/go (str (btu/get-server-url) "administration/catalogue-items/edit/" (btu/context-get :catalogue-item)))
    (btu/wait-page-loaded)
    (btu/wait-visible {:id :title-en :value "test-edit-catalogue-item EN"})
    (btu/screenshot "test-edit-catalogue-item-1.png")
    (is (= {"Organization" (str (btu/context-get :organization-name) " en")
            "Title (EN)" "test-edit-catalogue-item EN"
            "Title (FI)" "test-edit-catalogue-item FI"
            "Title (SV)" "test-edit-catalogue-item SV"
            "More info URL (optional) (EN)" ""
            "More info URL (optional) (FI)" ""
            "More info URL (optional) (SV)" ""
            "Form" "test-edit-catalogue-item form"
            "Workflow" "test-edit-catalogue-item workflow"
            "Resource" "test-edit-catalogue-item resource"}
           (slurp-fields :catalogue-item-editor)))
    (btu/fill-human :infourl-en "http://google.com")
    (btu/screenshot "test-edit-catalogue-item-2.png")
    (btu/scroll-and-click :save)
    (btu/wait-visible {:tag :h1 :fn/text "Catalogue item"})
    (btu/wait-page-loaded)
    (is (= {"Organization" (str (btu/context-get :organization-name) " en")
            "Title (EN)" "test-edit-catalogue-item EN"
            "Title (FI)" "test-edit-catalogue-item FI"
            "Title (SV)" "test-edit-catalogue-item SV"
            "More info (EN)" "http://google.com"
            "More info (FI)" ""
            "More info (SV)" ""
            "Form" "test-edit-catalogue-item form"
            "Workflow" "test-edit-catalogue-item workflow"
            "Resource" "test-edit-catalogue-item resource"
            "End" ""
            "Active" true}
           (dissoc (slurp-fields :catalogue-item) "Start")))
    (testing "after disabling the components"
      (with-user "owner"
        (organizations/set-organization-enabled! "owner" {:enabled false :organization/id (btu/context-get :organization-id)})
        (forms/set-form-enabled! {:id (btu/context-get :form) :enabled false})
        (resources/set-resource-enabled! {:id (btu/context-get :resource) :enabled false})
        (workflows/set-workflow-enabled! {:id (btu/context-get :workflow) :enabled false}))
      (testing "editing"
        (btu/go (str (btu/get-server-url) "administration/catalogue-items/edit/" (btu/context-get :catalogue-item)))
        (btu/wait-page-loaded)
        (btu/wait-visible {:id :title-en :value "test-edit-catalogue-item EN"})
        (is (= {"Organization" "Select..." ; unable to select a disabled org again
                "Title (EN)" "test-edit-catalogue-item EN"
                "Title (FI)" "test-edit-catalogue-item FI"
                "Title (SV)" "test-edit-catalogue-item SV"
                "More info URL (optional) (EN)" "http://google.com"
                "More info URL (optional) (FI)" ""
                "More info URL (optional) (SV)" ""
                "Form" "test-edit-catalogue-item form"
                "Workflow" "test-edit-catalogue-item workflow"
                "Resource" "test-edit-catalogue-item resource"}
               (dissoc (slurp-fields :create-catalogue-item) "Start"))))
      (testing "viewing"
        (btu/scroll-and-click :cancel)
        (btu/wait-page-loaded)
        (btu/wait-visible {:id :title :fn/has-text "test-edit-catalogue-item EN"})
        (is (= {"Organization" (str (btu/context-get :organization-name) " en")
                "Title (EN)" "test-edit-catalogue-item EN"
                "Title (FI)" "test-edit-catalogue-item FI"
                "Title (SV)" "test-edit-catalogue-item SV"
                "More info (EN)" "http://google.com"
                "More info (FI)" ""
                "More info (SV)" ""
                "Form" "test-edit-catalogue-item form"
                "Workflow" "test-edit-catalogue-item workflow"
                "Resource" "test-edit-catalogue-item resource"
                "End" ""
                "Active" true}
               (dissoc (slurp-fields :catalogue-item) "Start")))))))

(deftest test-form-editor
  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Forms")

    (testing "create form"
      (btu/scroll-and-click :create-form)
      (btu/wait-visible {:tag :h1 :fn/text "Create form"})
      (select-option "Organization" "nbn")
      (fill-form-field "Name" "Form editor test")
      (fill-form-field "EN" "Form Editor Test (EN)")
      (fill-form-field "FI" "Form Editor Test (FI)")
      (fill-form-field "SV" "Form Editor Test (SV)")
      (btu/scroll-and-click {:class :add-form-field})
      ;; using ids to fill the fields because the label structure is complicated
      (btu/wait-visible :fields-0-title-en)
      (btu/fill-human :fields-0-title-en "Text area (EN)")
      (btu/fill-human :fields-0-title-fi "Text area (FI)")
      (btu/fill-human :fields-0-title-sv "Text area (SV)")
      (btu/scroll-and-click :fields-0-placeholder-more-link)
      (btu/wait-visible :fields-0-placeholder-en)
      (btu/fill-human :fields-0-placeholder-en "Placeholder (EN)")
      (btu/fill-human :fields-0-placeholder-fi "Placeholder (FI)")
      (btu/fill-human :fields-0-placeholder-sv "Placeholder (SV)")
      (btu/scroll-and-click :fields-0-info-text-more-link)
      (btu/wait-visible :fields-0-info-text-en)
      (btu/fill-human :fields-0-info-text-en "") ; should not get passed as is blank
      (btu/fill-human :fields-0-info-text-fi " ")
      (btu/fill-human :fields-0-info-text-sv "")
      (btu/scroll-and-click :fields-0-type-texta)
      (btu/scroll-and-click :fields-0-optional)
      (btu/scroll-and-click :fields-0-additional-more-link)
      (btu/fill-human :fields-0-max-length "127")

      (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
      (btu/wait-visible :fields-1-title-en)
      (btu/fill-human :fields-1-title-en "Option list (EN)")
      (btu/fill-human :fields-1-title-fi "Option list (FI)")
      (btu/fill-human :fields-1-title-sv "Option list (SV)")
      (btu/scroll-and-click :fields-1-info-text-more-link)
      (btu/wait-visible :fields-1-info-text-en)
      (btu/fill-human :fields-1-info-text-en "Info text (EN)")
      (btu/fill-human :fields-1-info-text-fi "Info text (FI)")
      (btu/fill-human :fields-1-info-text-sv "Info text (SV)")
      (btu/scroll-and-click :fields-1-additional-more-link)
      (btu/clear :fields-1-id)
      (btu/fill-human :fields-1-id "opt")
      (btu/scroll-and-click :fields-1-type-option)
      (btu/scroll-and-click {:class :add-option})
      (btu/wait-visible :fields-1-options-0-key)
      (btu/fill-human :fields-1-options-0-key "true")
      (btu/fill-human :fields-1-options-0-label-en "Yes")
      (btu/fill-human :fields-1-options-0-label-fi "Kyllä")
      (btu/fill-human :fields-1-options-0-label-sv "Ja")
      (btu/scroll-and-click {:class :add-option})
      (btu/wait-visible :fields-1-options-1-key)
      (btu/fill-human :fields-1-options-1-key "false")
      (btu/fill-human :fields-1-options-1-label-en "No")
      (btu/fill-human :fields-1-options-1-label-fi "Ei")
      (btu/fill-human :fields-1-options-1-label-sv "Nej")

      ;; TODO create all field types?
      ;; TODO test validations?

      (btu/scroll-and-click :save))

    (testing "view form"
      (btu/wait-visible {:tag :h1 :fn/text "Form"})
      (btu/wait-page-loaded)
      (is (= {"Organization" "NBN"
              "Name" "Form editor test"
              "Title (EN)" "Form Editor Test (EN)"
              "Title (FI)" "Form Editor Test (FI)"
              "Title (SV)" "Form Editor Test (SV)"
              "Active" true}
             (slurp-fields :form)))
      (testing "preview"
        ;; the text is split into multiple DOM nodes so we need btu/has-text?, :fn/has-text is simpler for some reason
        (btu/wait-visible {:tag :button :fn/has-class :info-button})
        (is (btu/has-text? {:tag :label :class :application-field-label :fn/has-text "Text area (EN)"}
                           "(max 127 characters)"))
        (is (btu/has-text? {:tag :label :class :application-field-label :fn/has-text "Text area (EN)"}
                           "(optional)"))
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"})))

      (testing "info collapse can be toggled"
        (is (not (btu/visible? {:tag :div :fn/has-class :info-collapse})))
        (is (not (btu/visible? {:tag :div :fn/has-text "Info text (EN)"})))
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (btu/wait-visible {:tag :div :fn/has-class :info-collapse})
        (is (btu/visible? {:tag :div :fn/has-text "Info text (EN)"}))
        ;; TODO: figure out what to wait for
        (Thread/sleep 500)
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (btu/wait-invisible {:tag :div :fn/has-text "Info text (EN)"})
        (btu/wait-predicate #(not (btu/visible? {:tag :div :fn/has-text "Info text (EN)"})) {:timeout 30})
        (change-language :fi)
        (btu/wait-visible {:tag :label :class :application-field-label :fn/has-text "Text area (FI)"})
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Text area (FI)"}))
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (btu/wait-visible {:tag :div :fn/has-class :info-collapse})
        (is (btu/visible? {:tag :div :fn/has-text "Info text (FI)"}))))

    (testing "edit form"
      (change-language :en)
      (btu/wait-visible {:tag :h1 :fn/text "Form"})
      (btu/scroll-and-click {:fn/has-class :edit-form})
      (btu/wait-visible {:tag :h1 :fn/text "Edit form"})

      (testing "add description field"
        (btu/scroll-and-click {:class :add-form-field})
        (btu/scroll-and-click :fields-0-type-description)
        (btu/fill-human :fields-0-title-en "Description (EN)")
        (btu/fill-human :fields-0-title-fi "Description (FI)")
        (btu/fill-human :fields-0-title-sv "Description (SV)")

        (btu/scroll-and-click :save)
        (btu/wait-page-loaded)
        (btu/wait-visible {:tag :h1 :fn/has-text "Form"})
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"})))

      (testing "check that error message is present on field empty"
        (btu/scroll-and-click {:fn/has-class :edit-form})
        (btu/wait-page-loaded)
        (btu/wait-visible {:tag :h1 :fn/text "Edit form"})

        (btu/scroll-and-click :fields-0-type-description)
        (btu/scroll-and-click :fields-0-info-text-more-link)
        (btu/wait-visible :fields-0-info-text-en)
        (btu/fill-human :fields-0-info-text-en "Info text (EN)")
        (btu/fill-human :fields-0-info-text-fi "Info text (FI)")
        (btu/fill-human :fields-0-info-text-sv " ")

        (btu/scroll-and-click :save)

        (btu/wait-page-loaded)
        (btu/wait-visible {:tag :h1 :fn/has-text "Edit form"})
        (is (btu/visible? {:id :fields-0-info-text-sv :fn/has-class :is-invalid}))
        ;; :fn/has-text has trouble working for the whole "Field \"Field description (optional)\" is required." string
        (is (btu/visible? {:fn/has-class :invalid-feedback :fn/has-text "Field description (optional)"}))
        (is (btu/visible? {:fn/has-class :invalid-feedback :fn/has-text "is required"}))
        (is (btu/visible? {:fn/has-class :alert-danger :fn/has-text "Submission failed."})))

      (testing "successful save"
        (btu/fill-human :fields-0-info-text-sv "Info text (SV)")
        (btu/scroll-and-click :save)
        (btu/wait-page-loaded)
        (btu/wait-visible {:tag :h1 :fn/has-text "Form"})))

    (testing "fetch form via api"
      (let [form-id (Integer/parseInt (last (str/split (btu/get-url) #"/")))]
        (is (= {:form/id form-id
                :organization {:organization/id "nbn" :organization/name {:fi "NBN" :en "NBN" :sv "NBN"} :organization/short-name {:fi "NBN" :en "NBN" :sv "NBN"}}
                :form/internal-name "Form editor test"
                :form/external-title {:en "Form Editor Test (EN)"
                                      :fi "Form Editor Test (FI)"
                                      :sv "Form Editor Test (SV)"}
                :form/title "Form editor test" ; deprecated
                :form/fields [{:field/title {:fi "Description (FI)" :en "Description (EN)" :sv "Description (SV)"}
                               :field/info-text {:en "Info text (EN)", :fi "Info text (FI)", :sv "Info text (SV)"}
                               :field/type "description"
                               :field/id "fld2"
                               :field/max-length nil
                               :field/optional false}
                              {:field/placeholder {:fi "Placeholder (FI)" :en "Placeholder (EN)" :sv "Placeholder (SV)"}
                               :field/title {:fi "Text area (FI)" :en "Text area (EN)" :sv "Text area (SV)"}
                               :field/type "texta"
                               :field/id "fld1"
                               :field/max-length 127
                               :field/optional true}
                              {:field/title {:fi "Option list (FI)" :en "Option list (EN)" :sv "Option list (SV)"}
                               :field/info-text {:en "Info text (EN)", :fi "Info text (FI)", :sv "Info text (SV)"}
                               :field/type "option"
                               :field/id "opt"
                               :field/options [{:key "true" :label {:fi "Kyllä" :en "Yes" :sv "Ja"}}
                                               {:key "false" :label {:fi "Ei" :en "No" :sv "Nej"}}]
                               :field/optional false}]
                :form/errors nil
                :enabled true
                :archived false}
               (:body
                (http/get (str (btu/get-server-url) "/api/forms/" form-id)
                          {:as :json
                           :headers {"x-rems-api-key" "42"
                                     "x-rems-user-id" "handler"}}))))))
    (user-settings/delete-user-settings! "owner"))) ; clear language settings


(deftest test-workflow-create-edit
  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Workflows")
    (testing "create workflow"
      (btu/context-assoc! :workflow-title (str "test-workflow-create-edit " (btu/get-seed)))
      (btu/scroll-and-click :create-workflow)
      (btu/wait-visible {:tag :h1 :fn/text "Create workflow"})
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :workflow-title))
      (btu/scroll-and-click :type-decider)
      (btu/wait-page-loaded)
      (select-option "Handlers" "handler")
      (select-option "Handlers" "carl")
      (select-option "Forms" "Simple form")
      (btu/screenshot "test-workflow-create-edit-1.png")
      (btu/scroll-and-click :save))
    (testing "view workflow"
      (btu/wait-visible {:tag :h1 :fn/text "Workflow"})
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-2.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :workflow-title)
              "Type" "Decider workflow"
              "Handlers" "Carl Reviewer (carl@example.com), Hannah Handler (handler@example.com)"
              "Forms" "Simple form"
              "Active" true}
             (slurp-fields :workflow))))
    (testing "edit workflow"
      (btu/scroll-and-click {:fn/has-class :edit-workflow})
      (btu/wait-visible {:tag :h1 :fn/text "Edit workflow"})
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-3.png")
      (select-option "Organization" "Default")
      (fill-form-field "Title" " v2") ;; fill-form-field appends text to existing value
      (is (btu/disabled? :type-default)) ;; can't change type
      ;; removing an item is hard to script reliably, so let's just add one
      (select-option "Handlers" "reporter")
      (is (= "Simple form" (btu/get-element-text {:tag :div :id :workflow-forms}))) ; readonly field
      (btu/screenshot "test-workflow-create-edit-4.png")
      (btu/scroll-and-click :save))
    (testing "view workflow again"
      (btu/wait-visible {:tag :h1 :fn/text "Workflow"})
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-5.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "The Default Organization"
              "Title" (str (btu/context-get :workflow-title) " v2")
              "Type" "Decider workflow"
              "Handlers" "Carl Reviewer (carl@example.com), Hannah Handler (handler@example.com), Reporter (reporter@example.com)"
              "Forms" "Simple form"
              "Active" true}
             (slurp-fields :workflow)))
      (is (btu/visible? {:tag :a :fn/text "Simple form"})))))

(deftest test-blacklist
  (btu/with-postmortem
    (testing "set up resource & user"
      (test-helpers/create-resource! {:resource-ext-id "blacklist-test"})
      (users/add-user! {:userid "baddie" :name "Bruce Baddie" :email "bruce@example.com"}))
    (testing "add blacklist entry via resource page"
      (login-as "owner")
      (go-to-admin "Resources")
      (click-row-action [:resources] {:fn/text "blacklist-test"}
                        (select-button-by-label "View"))
      (btu/wait-visible {:tag :h1 :fn/text "Resource"})
      (btu/wait-page-loaded)
      (btu/wait-visible :blacklist)
      (is (= [{}] (slurp-rows :blacklist)))
      (btu/fill-human :blacklist-user "baddie\n")
      (btu/fill-human :blacklist-comment "This is a test.")
      (btu/screenshot "test-blacklist-1.png")
      (btu/scroll-and-click :blacklist-add)
      (btu/wait-visible {:css ".alert-success"})
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))
    (testing "check entry on resource page"
      (btu/wait-visible :blacklist)
      (is (= [{} ;; TODO remove the header row in slurp-rows
              {"resource" "blacklist-test"
               "user" "Bruce Baddie"
               "userid" "baddie"
               "email" "bruce@example.com"
               "added-by" "Owner"
               "comment" "This is a test."
               "commands" "Remove"}]
             (mapv #(dissoc % "added-at") (slurp-rows :blacklist)))))
    (testing "check entry on blacklist page"
      (go-to-admin "Blacklist")
      (btu/wait-visible {:tag :h1 :fn/text "Blacklist"})
      (btu/wait-page-loaded)
      (btu/wait-visible :blacklist)
      (is (= [{}
              {"resource" "blacklist-test"
               "user" "Bruce Baddie"
               "userid" "baddie"
               "email" "bruce@example.com"
               "added-by" "Owner"
               "comment" "This is a test."
               "commands" "Remove"}]
             (mapv #(dissoc % "added-at") (slurp-rows :blacklist)))))
    (testing "remove entry"
      (click-row-action [:blacklist] {:fn/text "baddie"}
                        (select-button-by-label "Remove"))
      (btu/wait-visible {:css ".alert-success"})
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (btu/wait-visible :blacklist)
      (is (= [{}] (slurp-rows :blacklist))))))

(deftest test-report
  (btu/with-postmortem
    (testing "set up form and submit an application using it"
      (btu/context-assoc! :form-title (str "Reporting Test Form " (btu/get-seed)))
      (btu/context-assoc! :form-id (test-helpers/create-form! {:form/internal-name (btu/context-get :form-title)
                                                               :form/external-title {:en (str (btu/context-get :form-title) " EN")
                                                                                     :fi (str (btu/context-get :form-title) " FI")
                                                                                     :sv (str (btu/context-get :form-title) " SV")}
                                                               :form/fields [{:field/id "desc"
                                                                              :field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                              :field/optional false
                                                                              :field/type :description}]}))
      (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {:handlers ["handler"]}))
      (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id) :workflow-id (btu/context-get :workflow-id)}))

      (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                      [(btu/context-get :catalogue-id)]
                                                                      (str "test-reporting " (btu/get-seed))))
      (test-helpers/command! {:type :application.command/save-draft
                              :application-id (btu/context-get :application-id)
                              :field-values [{:form (btu/context-get :form-id)
                                              :field "desc"
                                              :value "Tämä on monimutkainen arvo skandein varusteltuna!"}]
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/submit
                              :application-id (btu/context-get :application-id)
                              :actor "alice"})

      (btu/delete-downloaded-files! #"applications_.*\.csv")) ; make sure no report exists

    (testing "open report"
      (login-as "reporter")
      (go-to-admin "Reports")
      (btu/scroll-and-click :export-applications-button)
      (btu/wait-page-loaded)
      (btu/wait-visible {:tag :label :fn/text "Form"})
      (select-option* "Form" (btu/context-get :form-title))
      (btu/scroll-and-click :export-applications-button)
      (btu/wait-for-downloads #"applications_.*\.csv")) ; report has time in it that is difficult to control

    (testing "check report CSV"
      (let [application (get-application-from-api (btu/context-get :application-id))
            q (fn [s] (str "\"" s "\""))]
        (is (= ["\"Id\",\"External id\",\"Applicant\",\"Submitted\",\"State\",\"Resources\",\"description\""
                (str/join ","
                          [(:application/id application)
                           (q (:application/external-id application))
                           (q (get-in application [:application/applicant :name]))
                           (q (text/localize-time (get-in application [:application/first-submitted])))
                           (q "Applied")
                           (q "")
                           (q "Tämä on monimutkainen arvo skandein varusteltuna!")])]
               (->> #"applications_.*\.csv"
                    btu/downloaded-files
                    first
                    slurp
                    str/split-lines)))))))

(defn- get-organization-last-modified [organization-id]
  (text/localize-time (:organization/last-modified (organizations/get-organization-raw {:organization/id organization-id}))))

(deftest test-organizations
  (test-helpers/create-user! {:eppn "organization-owner1" :commonName "Organization Owner 1"
                              :mail "organization-owner1@example.com" :organizations [{:organization/id "Default"}]} :owner)
  (test-helpers/create-user! {:eppn "organization-owner2" :commonName "Organization Owner 2"
                              :mail "organization-owner2@example.com" :organizations [{:organization/id "Default"}]} :owner)

  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Organizations")

    (testing "create"
      (btu/scroll-and-click :create-organization)
      (btu/context-assoc! :organization-id (str "Organization id " (btu/get-seed)))
      (btu/context-assoc! :organization-name (str "Organization " (btu/get-seed)))
      (btu/wait-visible :id)
      (btu/fill-human :id (btu/context-get :organization-id))
      (btu/fill-human :short-name-en "SNEN")
      (btu/fill-human :short-name-fi "SNFI")
      (btu/fill-human :short-name-sv "SNSV")
      (btu/fill-human :name-en (str (btu/context-get :organization-name) " EN"))
      (btu/fill-human :name-fi (str (btu/context-get :organization-name) " FI"))
      (btu/fill-human :name-sv (str (btu/context-get :organization-name) " SV"))
      (select-option* "Owners" "Organization owner 1")
      (btu/scroll-and-click :add-review-email)
      (btu/scroll-and-click :add-review-email)

      (btu/wait-visible :review-emails-1-name-en)
      (btu/fill-human :review-emails-1-name-en "Review mail EN") ; fill second
      (btu/fill-human :review-emails-1-name-fi "Review mail FI")
      (btu/fill-human :review-emails-1-name-sv "Review mail SV")
      (btu/fill-human :review-emails-1-email "review.email@example.com")
      (btu/scroll-and-click {:css ".remove"}) ; remove first
      (btu/scroll-and-click :save)
      (btu/wait-visible {:css ".alert-success"})
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))

    (testing "view after creation"
      (btu/wait-visible :organization)
      (is (= {"Id" (btu/context-get :organization-id)
              "Short name (FI)" "SNFI"
              "Short name (EN)" "SNEN"
              "Short name (SV)" "SNSV"
              "Title (EN)" (str (btu/context-get :organization-name) " EN")
              "Title (FI)" (str (btu/context-get :organization-name) " FI")
              "Title (SV)" (str (btu/context-get :organization-name) " SV")
              "Owners" "Organization Owner 1 (organization-owner1@example.com)"
              "Name (FI)" "Review mail FI"
              "Name (SV)" "Review mail SV"
              "Name (EN)" "Review mail EN"
              "Email" "review.email@example.com"
              "Active" true
              "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
              "Modifier" "Owner (owner@example.com)"}
             (slurp-fields :organization))))

    (testing "edit after creation"
      (btu/scroll-and-click :edit-organization)
      (btu/wait-page-loaded)
      (btu/wait-visible :short-name-en)
      (select-option* "Owners" "Organization owner 2")
      (btu/clear :short-name-en)
      (btu/fill-human :short-name-en "SNEN2")
      (btu/clear :short-name-fi)
      (btu/fill-human :short-name-fi "SNFI2")
      (btu/clear :short-name-sv)
      (btu/fill-human :short-name-sv "SNSV2")
      (btu/scroll-and-click :save)
      (btu/wait-visible {:css ".alert-success"})
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))

      (testing "view after editing"
        (btu/wait-visible :organization)
        (is (= {"Id" (btu/context-get :organization-id)
                "Short name (FI)" "SNFI2"
                "Short name (EN)" "SNEN2"
                "Short name (SV)" "SNSV2"
                "Title (EN)" (str (btu/context-get :organization-name) " EN")
                "Title (FI)" (str (btu/context-get :organization-name) " FI")
                "Title (SV)" (str (btu/context-get :organization-name) " SV")
                "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                "Name (FI)" "Review mail FI"
                "Name (SV)" "Review mail SV"
                "Name (EN)" "Review mail EN"
                "Email" "review.email@example.com"
                "Active" true
                "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                "Modifier" "Owner (owner@example.com)"}
               (slurp-fields :organization)))))

    (testing "use after creation"
      (go-to-admin "Resources")
      (btu/wait-page-loaded)
      (btu/scroll-and-click :create-resource)
      (btu/wait-page-loaded)
      (btu/wait-visible :organization)
      (btu/fill-human :resid (str "resource for " (btu/context-get :organization-name)))
      (select-option* "Organization" (btu/context-get :organization-name))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:css ".alert-success"}))

    (testing "as organization owner"
      (logout)
      (login-as "organization-owner2")
      (go-to-admin "Organizations")

      (testing "list shows created organization"
        (btu/wait-visible :organizations)
        (let [orgs (slurp-rows :organizations)]
          (is (some #{{"short-name" "SNEN2"
                       "name" (str (btu/context-get :organization-name) " EN")
                       "active" true
                       "commands" "ViewDisableArchive"}}
                    orgs))))

      (testing "view from list"
        (click-row-action [:organizations]
                          {:fn/text (str (btu/context-get :organization-name) " EN")}
                          (select-button-by-label "View"))
        (btu/wait-page-loaded)
        (btu/wait-visible :organization)
        (is (= {"Id" (btu/context-get :organization-id)
                "Short name (FI)" "SNFI2"
                "Short name (EN)" "SNEN2"
                "Short name (SV)" "SNSV2"
                "Title (EN)" (str (btu/context-get :organization-name) " EN")
                "Title (FI)" (str (btu/context-get :organization-name) " FI")
                "Title (SV)" (str (btu/context-get :organization-name) " SV")
                "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                "Name (FI)" "Review mail FI"
                "Name (SV)" "Review mail SV"
                "Name (EN)" "Review mail EN"
                "Email" "review.email@example.com"
                "Active" true
                "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                "Modifier" "Owner (owner@example.com)"}
               (slurp-fields :organization))))

      (testing "edit as organization owner"
        (btu/scroll-and-click :edit-organization)
        (btu/wait-page-loaded)
        (btu/wait-visible :short-name-en)
        (btu/clear :short-name-en)
        (btu/fill-human :short-name-en "SNEN")
        (btu/clear :short-name-fi)
        (btu/fill-human :short-name-fi "SNFI")
        (btu/clear :short-name-sv)
        (btu/fill-human :short-name-sv "SNSV")
        (btu/scroll-and-click :save)
        (btu/wait-visible {:css ".alert-success"})
        (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))

        (testing "view after editing"
          (btu/wait-visible :organization)
          (is (= {"Id" (btu/context-get :organization-id)
                  "Short name (FI)" "SNFI"
                  "Short name (EN)" "SNEN"
                  "Short name (SV)" "SNSV"
                  "Title (EN)" (str (btu/context-get :organization-name) " EN")
                  "Title (FI)" (str (btu/context-get :organization-name) " FI")
                  "Title (SV)" (str (btu/context-get :organization-name) " SV")
                  "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                  "Name (FI)" "Review mail FI"
                  "Name (SV)" "Review mail SV"
                  "Name (EN)" "Review mail EN"
                  "Email" "review.email@example.com"
                  "Active" true
                  "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                  "Modifier" "Organization Owner 2 (organization-owner2@example.com)"}
                 (slurp-fields :organization))))))))

(deftest test-small-navbar
  (testing "create a test application with the API to have another page to navigate to"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-small-navbar"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"}))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-catalogue)
    (btu/set-window-size 400 600) ; small enough for mobile
    (btu/wait-invisible :small-navbar)
    (btu/scroll-and-click {:css ".navbar-toggler"})
    (btu/wait-visible :small-navbar)
    (btu/screenshot "small-navbar.png")
    (btu/scroll-and-click [:small-navbar {:tag :a :fn/text "Applications"}])
    (btu/wait-invisible :small-navbar) ; menu should be hidden
    (btu/wait-visible {:tag :h1 :fn/text "Applications"})
    (btu/scroll-and-click {:css ".navbar-toggler"})
    (btu/scroll-and-click [:small-navbar {:tag :button :fn/text "FI"}])
    (btu/wait-invisible :small-navbar) ; menu should be hidden
    (btu/wait-visible {:tag :h1 :fn/text "Hakemukset"})
    (user-settings/delete-user-settings! "alice") ; clear language settings
    (is true)))  ; avoid no assertions warning
