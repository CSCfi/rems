(ns ^:browser rems.test-browser
  "REMS Browser tests.

  For the test database, you need to run these tests in the :test profile to get the right :database-url and :port.

  For development development tests, you can run against a running instance with:

  (rems.browser-test-util/init-driver! :chrome \"http://localhost:3000/\" :development)

  NB: Don't use etaoin directly but use it from the `browser-test-util` library that removes the need to pass the driver."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.rpl.specter :refer [select ALL]]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.db.user-settings :as user-settings]
            [rems.standalone]
            [rems.browser-test-util :as btu]))

(comment ; convenience for development testing
  (btu/init-driver! :chrome "http://localhost:3000/" :development))

(use-fixtures :each btu/fixture-driver)

(use-fixtures :once btu/test-dev-or-standalone-fixture)

;;; common functionality

(defn login-as [username]
  (btu/set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
  (btu/go (btu/get-server-url))
  (btu/screenshot (io/file btu/reporting-dir "landing-page.png"))
  (btu/scroll-and-click {:css ".login-btn"})
  (btu/screenshot (io/file btu/reporting-dir "login-page.png"))
  (btu/scroll-and-click [{:class "users"} {:tag :a :fn/text username}])
  (btu/wait-visible :logout)
  (btu/screenshot (io/file btu/reporting-dir "logged-in.png")))

(defn logout []
  (btu/scroll-and-click :logout)
  (btu/wait-visible {:css ".login-component"}))

(defn click-navigation-menu [link-text]
  (btu/scroll-and-click [:big-navbar {:tag :a :fn/text link-text}]))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
  (btu/wait-page-loaded)
  (btu/screenshot (io/file btu/reporting-dir "catalogue-page.png")))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (btu/wait-visible {:tag :h1 :fn/text "Applications"})
  (btu/wait-page-loaded)
  (btu/screenshot (io/file btu/reporting-dir "applications-page.png")))


(defn click-administration-menu [link-text]
  (btu/scroll-and-click [:administration-menu {:tag :a :fn/text link-text}]))


(defn go-to-admin [link-text]
  (click-administration-menu link-text)
  (btu/wait-visible {:tag :h1 :fn/text link-text})
  (btu/wait-page-loaded)
  (btu/screenshot (io/file btu/reporting-dir (str "administration-page-" (str/replace link-text " " "-") ".png"))))

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
  (btu/screenshot (io/file btu/reporting-dir "application-page.png")))



;;; application page

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

(defn set-date [label date]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/text label}]
                                 :for)]
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
     id date)))

(defn select-option [label option]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/has-text label}]
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

(defn- get-application-from-api [application-id]
  (:body
   (http/get (str (btu/get-server-url) "/api/applications/" application-id)
             {:as :json
              :headers {"x-rems-api-key" "42"
                        "x-rems-user-id" "handler"}})))

;;; tests

(deftest test-new-application
  (btu/with-postmortem {:dir btu/reporting-dir}
    (login-as "alice")

    (testing "create application"
      (go-to-catalogue)
      (add-to-cart "Default workflow")
      (apply-for-resource "Default workflow")

      (btu/context-assoc! :application-id (get-application-id))

      (let [application (:body
                         (http/get (str (btu/get-server-url) "/api/applications/" (btu/context-get :application-id))
                                   {:as :json
                                    :headers {"x-rems-api-key" "42"
                                              "x-rems-user-id" "handler"}}))
            form-id (get-in application [:application/forms 0 :form/id])
            description-field-id (get-in application [:application/forms 0 :form/fields 1 :field/id])
            description-field-selector (keyword (str "form-" form-id "-field-" description-field-id))
            attachment-field (get-in application [:application/forms 0 :form/fields 7])
            attachment-field-selector (keyword (str "form-" form-id "-field-" (:field/id attachment-field) "-input"))]
        (is (= "attachment" (:field/type attachment-field))) ;; sanity check

        (fill-form-field "Application title field" "Test name")
        (fill-form-field "Text field" "Test")
        (fill-form-field "Text area" "Test2")
        (set-date "Date field" "2050-01-02")
        (fill-form-field "Email field" "user@example.com")
        (btu/upload-file attachment-field-selector "test-data/test.txt")
        (btu/wait-predicate #(= ["test.txt"] (get-attachments)))

        (is (not (btu/field-visible? "Conditional field"))
            "Conditional field is not visible before selecting option")

        (select-option "Option list" "First option")
        (btu/wait-predicate #(btu/field-visible? "Conditional field"))
        (fill-form-field "Conditional field" "Conditional")
        ;; pick two options for the multi-select field:
        (btu/check-box "Option2")
        (btu/check-box "Option3")
        ;; leave "Text field with max length" empty
        ;; leave "Text are with max length" empty

        (accept-licenses)
        (send-application)

        (is (= "Applied" (btu/get-element-text :application-state)))

        (testing "check a field answer"
          (is (= "Test name" (btu/get-element-text description-field-selector))))

        (testing "fetch application from API"
          (let [application (get-application-from-api (btu/context-get :application-id))]
            (btu/context-assoc! :attachment-id (get-in application [:application/attachments 0 :attachment/id]))

            (testing "see application on applications page"
              (go-to-applications)

              (is (= {:id (btu/context-get :application-id)
                      :resource "Default workflow"
                      :state "Applied"
                      :description "Test name"}
                     (get-application-summary (btu/context-get :application-id)))))

            (testing "attachments"
              (is (= [{:attachment/id (btu/context-get :attachment-id)
                       :attachment/filename "test.txt"
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
                      ["attachment" (str (btu/context-get :attachment-id))]
                      ["option" "Option1"]
                      ["text" "Conditional"]
                      ["multiselect" "Option2 Option3"]
                      ["label" ""]
                      ["text" ""]
                      ["texta" ""]]
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
              (testing "check a field answer"
                (is (= "Test name" (btu/get-element-text description-field-selector)))))))))))

(deftest test-handling
  (let [applicant "alice"
        handler "developer"
        form-id (test-data/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                        :field/optional false
                                                        :field/type :description}]})
        catalogue-id (test-data/create-catalogue-item! {:form-id form-id})
        application-id (test-data/create-draft! applicant
                                                [catalogue-id]
                                                "test-handling")]
    (test-data/command! {:type :application.command/submit
                         :application-id application-id
                         :actor applicant})
    (btu/with-postmortem {:dir btu/reporting-dir}
      (login-as handler)
      (testing "handler should see todos on logging in"
        (btu/wait-visible :todo-applications))
      (testing "handler should see description of application"
        (btu/wait-visible {:class :application-description :fn/text "test-handling"}))
      (let [app-button {:tag :a :href (str "/application/" application-id)}]
        (testing "handler should see view button for application"
          (btu/wait-visible app-button))
        (btu/scroll-and-click app-button))
      (testing "handler should see application after clicking on View"
        (btu/wait-visible {:tag :h1 :fn/has-text "test-handling"}))
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
      (testing "attachments visible in eventlog"
        (is (= ["test.txt" "test-fi.txt"]
               (get-attachments {:css "div.event a.attachment-link"})))))))

(deftest test-guide-page
  (btu/with-postmortem {:dir btu/reporting-dir}
    (btu/go (str (btu/get-server-url) "guide"))
    (btu/wait-visible {:tag :h1 :fn/text "Component Guide"})
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (btu/query-all {:class :example}))))))

(deftest test-language-change
  (btu/with-postmortem {:dir btu/reporting-dir}
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

    (testing "changing language while logged i"
      (change-language :en)
      (btu/wait-visible {:tag :h1 :fn/text "Catalogue"}))
    (is true))) ; avoid no assertions warning

(defn slurp-fields [selector]
  (->> (for [row (btu/query-all [selector {:fn/has-class :row}])
             :let [k (btu/get-element-text-el (btu/child row {:tag :label}))
                   [value-el] (btu/children row {:css ".form-control"})]
             :when value-el]
         [k (str/trim (btu/get-element-text-el value-el))])
       (into {})))

(defn slurp-rows [& selectors]
  (for [row (btu/query-all (vec (concat selectors [{:css "tr"}])))]
    (->> (for [td (btu/children row {:css "td"})
               :let [k (str/trim (btu/get-element-attr-el td "class"))
                     v (btu/get-element-text-el td)]]
           [k (str/trim v)])
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

(defn create-license []
  (testing "create license"
    (btu/with-postmortem {:dir btu/reporting-dir}
      (go-to-admin "Licenses")
      (btu/scroll-and-click :create-license)
      (btu/wait-visible {:tag :h1 :fn/text "Create license"})
      (select-option "Organization" "nbn")
      (btu/scroll-and-click :licensetype-link)
      (fill-form-field "License name" (str (btu/context-get :license-name) " EN") {:index 1})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
      (fill-form-field "License name" (str (btu/context-get :license-name) " FI") {:index 2})
      (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
      (fill-form-field "License name" (str (btu/context-get :license-name) " SV") {:index 3})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 3})
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-license.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "License"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-license.png"))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title (EN)" (str (btu/context-get :license-name) " EN")
              "Title (FI)" (str (btu/context-get :license-name) " FI")
              "Title (SV)" (str (btu/context-get :license-name) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" ""}
             (slurp-fields :license)))
      (go-to-admin "Licenses")
      (btu/wait-visible {:tag :h1 :fn/text "Licenses"})
      (is (some #{{"organization" "NBN"
                   "title" (str (btu/context-get :license-name) " EN")
                   "type" "link"
                   "active" ""
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
              "Active" ""}
             (slurp-fields :license))))))

(defn create-resource []
  (testing "create resource"
    (btu/with-postmortem {:dir btu/reporting-dir}
      (go-to-admin "Resources")
      (btu/scroll-and-click :create-resource)
      (btu/wait-visible {:tag :h1 :fn/text "Create resource"})
      (select-option "Organization" "nbn")
      (fill-form-field "Resource identifier" (btu/context-get :resid))
      (select-option "License" (str (btu/context-get :license-name) " EN"))
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-resource.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Resource"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-resource.png"))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Resource" (btu/context-get :resid)
              "Active" ""}
             (slurp-fields :resource)))
      (is (= (str "License \"" (btu/context-get :license-name) " EN\"")
             (btu/get-element-text [:licenses {:class :license-title}])))
      (go-to-admin "Resources")
      (is (some #(= (btu/context-get :resid) (get % "title"))
                (slurp-rows :resources))))))

(defn create-form []
  (testing "create form"
    (btu/with-postmortem {:dir btu/reporting-dir}
      (go-to-admin "Forms")
      (btu/scroll-and-click :create-form)
      (btu/wait-visible {:tag :h1 :fn/text "Create form"})
      (select-option "Organization" "nbn")
      (fill-form-field "Form name" (btu/context-get :form-name))
      ;; TODO: create fields
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-form.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Form"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-form.png"))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :form-name)
              "Active" ""}
             (slurp-fields :form)))
      (go-to-admin "Forms")
      (is (some #(= (btu/context-get :form-name) (get % "title"))
                (slurp-rows :forms))))))

(defn create-workflow []
  (testing "create workflow"
    (btu/with-postmortem {:dir btu/reporting-dir}
      (go-to-admin "Workflows")
      (btu/scroll-and-click :create-workflow)
      (btu/wait-visible {:tag :h1 :fn/text "Create workflow"})
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :workflow-name))
      ;; Default workflow is already checked
      (select-option "Handlers" "handler")
      ;; No form
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-workflow.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Workflow"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-workflow.png"))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :workflow-name)
              "Type" "Default workflow"
              "Handlers" "Hannah Handler (handler@example.com)"
              "Active" ""}
             (slurp-fields :workflow)))
      (go-to-admin "Workflows")
      (is (some #(= (btu/context-get :workflow-name) (get % "title"))
                (slurp-rows :workflows))))))

(defn create-catalogue-item []
  (testing "create catalogue item"
    (btu/with-postmortem {:dir btu/reporting-dir}
      (go-to-admin "Catalogue items")
      (btu/scroll-and-click :create-catalogue-item)
      (select-option "Organization" "nbn")
      (fill-form-field "Name" (btu/context-get :catalogue-item-name) {:index 1})
      (fill-form-field "Name" (str (btu/context-get :catalogue-item-name) " FI") {:index 2})
      (fill-form-field "Name" (str (btu/context-get :catalogue-item-name) " SV") {:index 3})
      (select-option "Workflow" (btu/context-get :workflow-name))
      (select-option "Resource" (btu/context-get :resid))
      (select-option "Form" (btu/context-get :form-name))
      (btu/screenshot (io/file btu/reporting-dir "about-to-create-catalogue-item.png"))
      (btu/scroll-and-click :save)
      (btu/wait-visible {:tag :h1 :fn/text "Catalogue item"})
      (btu/wait-page-loaded)
      (btu/screenshot (io/file btu/reporting-dir "created-catalogue-item.png"))
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
              "Active" ""
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
  (btu/screenshot (io/file btu/reporting-dir "about-to-enable-catalogue-item.png"))
  (btu/scroll-and-click {:tag :button :fn/text "Enable"})
  (btu/wait-page-loaded)
  (btu/screenshot (io/file btu/reporting-dir "enabled-catalogue-item.png"))
  (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))


(deftest test-create-catalogue-item
  (btu/with-postmortem {:dir btu/reporting-dir}
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

(deftest test-form-editor
  (btu/with-postmortem {:dir btu/reporting-dir}
    (login-as "owner")
    (go-to-admin "Forms")
    (btu/scroll-and-click :create-form)
    (btu/wait-visible {:tag :h1 :fn/text "Create form"})
    (select-option "Organization" "nbn")
    (fill-form-field "Form name" "Form editor test")
    (btu/scroll-and-click {:class :add-form-field})
    ;; using ids to fill the fields because the label structure is complicated
    (btu/wait-visible :fields-0-title-en)
    (btu/fill-human :fields-0-title-en "Text area (EN)")
    (btu/fill-human :fields-0-title-fi "Text area (FI)")
    (btu/fill-human :fields-0-title-sv "Text area (SV)")
    (btu/fill-human :fields-0-placeholder-en "Placeholder (EN)")
    (btu/fill-human :fields-0-placeholder-fi "Placeholder (FI)")
    (btu/fill-human :fields-0-placeholder-sv "Placeholder (SV)")
    (btu/scroll-and-click :fields-0-type-texta)
    (btu/scroll-and-click :fields-0-optional)
    (btu/fill-human :fields-0-max-length "127")

    (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
    (btu/wait-visible :fields-1-title-en)
    (btu/fill-human :fields-1-title-en "Option list (EN)")
    (btu/fill-human :fields-1-title-fi "Option list (FI)")
    (btu/fill-human :fields-1-title-sv "Option list (SV)")
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

    (btu/scroll-and-click :save)
    (btu/wait-visible {:tag :h1 :fn/text "Form"})
    (btu/wait-page-loaded)

    (is (= {"Organization" "NBN"
            "Title" "Form editor test"
            "Active" ""}
           (slurp-fields :form)))
    ;; the text is split into multiple DOM nodes so we need to get fancy
    (let [text-area-label (btu/query {:tag :label :class :application-field-label :fn/has-text "Text area (EN)"})]
      (is text-area-label)
      (is (btu/visible-el? text-area-label))
      (is (= "Text area (EN) (max 127 characters) (optional)" (btu/get-element-text-el text-area-label))))
    (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"}))

    (btu/scroll-and-click {:fn/has-class :edit-form})
    (btu/wait-visible {:tag :h1 :fn/text "Edit form"})

    ;; add one field to the beginning
    (btu/scroll-and-click {:class :add-form-field})
    (btu/scroll-and-click :fields-0-type-description)
    (btu/fill-human :fields-0-title-en "Description (EN)")
    (btu/fill-human :fields-0-title-fi "Description (FI)")
    (btu/fill-human :fields-0-title-sv "Description (SV)")

    (btu/scroll-and-click :save)
    (btu/wait-visible {:tag :h1 :fn/text "Form"})
    (btu/wait-page-loaded)
    (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"}))

    (testing "fetch via api"
      (let [form-id (Integer/parseInt (last (str/split (btu/get-url) #"/")))]
        (is (= {:form/id form-id
                :form/organization {:organization/id "nbn" :organization/name "NBN"}
                :form/title "Form editor test"
                :form/fields [{:field/placeholder {:fi "" :en "" :sv ""}
                               :field/title {:fi "Description (FI)" :en "Description (EN)" :sv "Description (SV)"}
                               :field/type "description"
                               :field/id "fld3"
                               :field/max-length nil
                               :field/optional false}
                              {:field/placeholder {:fi "Placeholder (FI)" :en "Placeholder (EN)" :sv "Placeholder (SV)"}
                               :field/title {:fi "Text area (FI)" :en "Text area (EN)" :sv "Text area (SV)"}
                               :field/type "texta"
                               :field/id "fld1"
                               :field/max-length 127
                               :field/optional true}
                              {:field/title {:fi "Option list (FI)" :en "Option list (EN)" :sv "Option list (SV)"}
                               :field/type "option"
                               :field/id "fld2"
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
                                     "x-rems-user-id" "handler"}}))))))))
