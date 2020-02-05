(ns ^:browser rems.test-browser
  "You need to run these tests in the :test profile to get the right :database-url and :port"
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [select ALL]]
            [etaoin.api :refer :all]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.db.user-settings :as user-settings]
            [rems.standalone])
  (:import (java.net SocketException)))

(defonce ^:private test-url (atom "http://localhost:3001/"))
(defonce ^:dynamic *driver* nil)
(defonce ^:private test-mode (atom :test))

(defn- delete-files [dir]
  (doseq [file (.listFiles dir)]
    (io/delete-file file true)))

(def reporting-dir
  (doto (io/file "browsertest-errors")
    (.mkdirs)
    (delete-files)))

(defn development-driver
  "Starts the driver for development use.

  Opens a regular browser that can be commanded while creating or debugging tests."
  []
  (when *driver* (quit *driver*))
  (def *driver* (boot-driver :chrome
                             {:args ["--lang=en-US"]
                              :prefs {"intl.accept_languages" "en-US"}}))
  (delete-cookies *driver*) ; start with a clean slate
  (reset! test-url "http://localhost:3000/")
  (reset! test-mode :development)
  *driver*)

(comment
  (development-driver))

(defn fixture-driver
  "Executes a test running a driver.
   Bounds a driver with the global *driver* variable."
  [f]
  (if (= :development @test-mode)
    (f)
    ;; TODO: these args don't affect the date format of <input type="date"> elements; figure out a reliable way to set it
    (let [run #(with-chrome-headless {:args ["--lang=en-US"]
                                      :prefs {"intl.accept_languages" "en-US"}}
                 driver
                 (binding [*driver* driver]
                   (delete-cookies *driver*) ; start with a clean slate
                   (f)))]
      (try
        (run)
        (catch SocketException e
          (log/warn e "WebDriver failed to start, retrying...")
          (run))))))

(defn fixture-standalone [f]
  (if (= :development @test-mode)
    (f)
    (do
      (mount/start)
      (migrations/migrate ["migrate"] (select-keys rems.config/env [:database-url]))
      (test-data/create-test-data!)
      (f)
      (migrations/migrate ["reset"] (select-keys rems.config/env [:database-url]))
      (mount/stop))))

(defn smoke-test [f]
  (let [response (http/get (str @test-url "js/app.js"))]
    (assert (= 200 (:status response))
            (str "Failed to load app.js: " response))
    (f)))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

(use-fixtures
  :once
  standalone-fixture
  smoke-test)

;;; basic navigation

(defn scroll-and-click
  "Wait a button to become visible, scroll it to middle
  (to make sure it's not hidden under navigation) and click."
  [driver q & [opt]]
  (doto driver
    (wait-visible q opt)
    (scroll-query q {"block" "center"})
    (click q)))

(defn login-as [username]
  (doto *driver*
    (set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
    (go @test-url)
    (screenshot (io/file reporting-dir "landing-page.png"))
    (scroll-and-click {:css ".login-btn"})
    (screenshot (io/file reporting-dir "login-page.png"))
    (scroll-and-click [{:class "users"} {:tag :a, :fn/text username}])
    (wait-visible :logout)
    (screenshot (io/file reporting-dir "logged-in.png"))))

(defn logout []
  (scroll-and-click *driver* :logout)
  (wait-visible *driver* {:css ".login-component"}))

(defn- wait-page-loaded []
  (wait-invisible *driver* {:css ".fa-spinner"}))

(defn click-navigation-menu [link-text]
  (scroll-and-click *driver* [:big-navbar {:tag :a, :fn/text link-text}]))

(defn click-administration-menu [link-text]
  (scroll-and-click *driver* [:administration-menu {:tag :a, :fn/text link-text}]))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (wait-visible *driver* {:tag :h1, :fn/text "Catalogue"})
  (wait-page-loaded)
  (screenshot *driver* (io/file reporting-dir "catalogue-page.png")))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (wait-visible *driver* {:tag :h1, :fn/text "Applications"})
  (wait-page-loaded)
  (screenshot *driver* (io/file reporting-dir "applications-page.png")))

(defn go-to-admin-licenses []
  (click-administration-menu "Licenses")
  (wait-visible *driver* {:tag :h1, :fn/text "Licenses"})
  (wait-page-loaded)
  (screenshot *driver* (io/file reporting-dir "administration-licenses-page.png")))

(defn go-to-admin-resources []
  (click-administration-menu "Resources")
  (wait-visible *driver* {:tag :h1, :fn/text "Resources"})
  (wait-page-loaded)
  (screenshot *driver* (io/file reporting-dir "administration-resources-page.png")))

(defn change-language [language]
  (scroll-and-click *driver* [{:css ".language-switcher"} {:fn/text (.toUpperCase (name language))}]))

;;; catalogue page

(defn add-to-cart [resource-name]
  (scroll-and-click *driver* [{:css "table.catalogue"}
                              {:fn/text resource-name}
                              {:xpath "./ancestor::tr"}
                              {:css ".add-to-cart"}]))

(defn apply-for-resource [resource-name]
  (scroll-and-click *driver* [{:css "table.cart"}
                              {:fn/text resource-name}
                              {:xpath "./ancestor::tr"}
                              {:css ".apply-for-catalogue-items"}])
  (wait-visible *driver* {:tag :h1, :fn/has-text "Application"})
  (wait-page-loaded)
  (screenshot *driver* (io/file reporting-dir "application-page.png")))

;;; application page

(defn fill-form-field
  "Fills a form field named by `label` with `text`.

  Optionally give `:index` when several items match. It starts from 1."
  [label text & [opts]]
  (assert (> (:index opts 1) 0) "indexing starts at 1") ; xpath uses 1, let's keep the convention though we don't use xpath here because it will likely not work

  (let [el (nth (query-all *driver* [{:css ".fields"}
                                     {:tag :label :fn/has-text label}])
                (dec (:index opts 1)))
        id (get-element-attr-el *driver* el :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (fill-human *driver* {:id id} text)))

(defn set-date [label date]
  (let [id (get-element-attr *driver* [{:css ".fields"}
                                       {:tag :label, :fn/text label}]
                             :for)]
    ;; XXX: The date format depends on operating system settings and is unaffected by browser locale,
    ;;      so we cannot reliably know the date format to type into the date field and anyways WebDriver
    ;;      support for date fields seems buggy. Changing the field with JavaScript is more reliable.
    (js-execute *driver*
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
  (let [id (get-element-attr *driver* [{:css ".fields"}
                                       {:tag :label, :fn/has-text label}]
                             :for)]
    (fill *driver* {:id id} (str option "\n"))))

(defn field-visible? [label]
  (visible? *driver* [{:css ".fields"}
                      {:tag :label :fn/has-text label}]))

(defn check-box [value]
  ;; XXX: assumes that the checkbox is unchecked
  (scroll-and-click *driver* [{:css (str "input[value='" value "']")}]))

(defn accept-licenses []
  (doto *driver*
    (scroll-and-click :accept-licenses-button)
    (wait-visible :has-accepted-licenses)))

(defn send-application []
  (doto *driver*
    (scroll-and-click :submit)
    (wait-visible :status-success)
    (wait-has-class :apply-phase "completed")))

(defn get-application-id []
  (last (str/split (get-url *driver*) #"/")))

(defn get-attachments
  ([]
   (get-attachments {:css "a.attachment-link"}))
  ([selector]
   (mapv (partial get-element-text-el *driver*) (query-all *driver* selector))))

;; applications page

(defn get-application-summary [application-id]
  (let [row (query *driver* [{:css "table.my-applications"}
                             {:tag :tr, :data-row application-id}])]
    {:id application-id
     :description (get-element-text-el *driver* (child *driver* row {:css ".description"}))
     :resource (get-element-text-el *driver* (child *driver* row {:css ".resource"}))
     :state (get-element-text-el *driver* (child *driver* row {:css ".state"}))}))

;;; tests

(deftest test-new-application
  (with-postmortem *driver* {:dir reporting-dir}
    (login-as "alice")

    (testing "create application"
      (go-to-catalogue)
      (add-to-cart "Default workflow")
      (apply-for-resource "Default workflow")

      (let [application-id (get-application-id)
            application (:body
                         (http/get (str +test-url+ "/api/applications/" application-id)
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
        (upload-file *driver* attachment-field-selector "test-data/test.txt")
        (wait-predicate #(= ["test.txt"] (get-attachments)))

        (is (not (field-visible? "Conditional field"))
            "Conditional field is not visible before selecting option")
        (select-option "Option list" "First option")
        (wait-predicate #(field-visible? "Conditional field"))
        (fill-form-field "Conditional field" "Conditional")
        ;; pick two options for the multi-select field:
        (check-box "Option2")
        (check-box "Option3")
        ;; leave "Text field with max length" empty
        ;; leave "Text are with max length" empty

        (accept-licenses)
        (send-application)
        (is (= "Applied" (get-element-text *driver* :application-state)))

        (testing "check a field answer"
          (is (= "Test name" (get-element-text *driver* description-field-selector))))

        (testing "see application on applications page"
          (go-to-applications)
          (let [summary (get-application-summary application-id)]
            (is (= "Default workflow" (:resource summary)))
            (is (= "Applied" (:state summary)))
            ;; don't bother trying to predict the external id:
            (is (.contains (:description summary) "Test name"))))

        (testing "fetch application from API"
          (let [application (:body
                             (http/get (str +test-url+ "/api/applications/" application-id)
                                       {:as :json
                                        :headers {"x-rems-api-key" "42"
                                                  "x-rems-user-id" "handler"}}))
                attachment-id (get-in application [:application/attachments 0 :attachment/id])]
            (testing "attachments"
              (is (= [{:attachment/id attachment-id
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
                      ["attachment" (str attachment-id)]
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
              (scroll-and-click *driver* [{:css "table.my-applications"}
                                          {:tag :tr :data-row application-id}
                                          {:css ".btn-primary"}])
              (wait-visible *driver* {:tag :h1, :fn/has-text "Application"})
              (wait-page-loaded)
              (testing "check a field answer"
                (is (= "Test name" (get-element-text *driver* description-field-selector)))))))))))

(deftest test-handling
  (let [applicant "alice"
        handler "developer"
        form-id (test-data/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus"}
                                                        :field/optional false
                                                        :field/type :description}]})
        catalogue-id (test-data/create-catalogue-item! {:form-id form-id})
        application-id (test-data/create-draft! applicant
                                                [catalogue-id]
                                                "test-handling")]
    (test-data/command! {:type :application.command/submit
                         :application-id application-id
                         :actor applicant})
    (with-postmortem *driver* {:dir reporting-dir}
      (login-as handler)
      (testing "handler should see todos on logging in"
        (wait-visible *driver* :todo-applications))
      (testing "handler should see description of application"
        (wait-visible *driver* {:class :application-description :fn/text "test-handling"}))
      (let [app-button {:tag :a :href (str "/application/" application-id)}]
        (testing "handler should see view button for application"
          (wait-visible *driver* app-button))
        (scroll-and-click *driver* app-button))
      (testing "handler should see application after clicking on View"
        (wait-visible *driver* {:tag :h1 :fn/has-text "test-handling"}))
      (testing "open the approve form"
        (scroll-and-click *driver* :approve-reject-action-button))
      (testing "add a comment and two attachments"
        (wait-visible *driver* :comment-approve-reject)
        (fill-human *driver* :comment-approve-reject "this is a comment")
        (upload-file *driver* :upload-approve-reject-input "test-data/test.txt")
        (wait-visible *driver* [{:css "a.attachment-link"}])
        (upload-file *driver* :upload-approve-reject-input "test-data/test-fi.txt")
        (wait-predicate #(= ["test.txt" "test-fi.txt"]
                            (get-attachments))))
      (testing "add and remove a third attachment"
        (upload-file *driver* :upload-approve-reject-input "resources/public/img/rems_logo_en.png")
        (wait-predicate #(= ["test.txt" "test-fi.txt" "rems_logo_en.png"]
                            (get-attachments)))
        (let [buttons (query-all *driver* {:css "button.remove-attachment-approve-reject"})]
          (click-el *driver* (last buttons)))
        (wait-predicate #(= ["test.txt" "test-fi.txt"]
                            (get-attachments))))
      (testing "approve"
        (scroll-and-click *driver* :approve)
        (wait-predicate #(= "Approved" (get-element-text *driver* :application-state))))
      (testing "attachments visible in eventlog"
        (is (= ["test.txt" "test-fi.txt"]
               (get-attachments {:css "div.event a.attachment-link"})))))))

(deftest test-guide-page
  (with-postmortem *driver* {:dir reporting-dir}
    (go *driver* (str @test-url "guide"))
    (wait-visible *driver* {:tag :h1 :fn/text "Component Guide"})
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (query-all *driver* {:class :example}))))))

(deftest test-language-change
  (with-postmortem *driver* {:dir reporting-dir}
    (testing "default language is English"
      (go *driver* @test-url)
      (wait-visible *driver* {:tag :h1 :fn/text "Welcome to REMS"})
      (login-as "alice")
      (wait-visible *driver* {:tag :h1, :fn/text "Catalogue"})
      (wait-page-loaded))

    (testing "changing language while logged out"
      (logout)
      (wait-visible *driver* {:tag :h1 :fn/text "Welcome to REMS"})
      (change-language :fi)
      (wait-visible *driver* {:tag :h1 :fn/text "Tervetuloa REMSiin"}))

    (testing "changed language must persist after login"
      (login-as "alice")
      (wait-visible *driver* {:tag :h1, :fn/text "Aineistoluettelo"})
      (wait-page-loaded))

    (testing "wait for language change to show in the db"
      (wait-predicate #(= :fi (:language (user-settings/get-user-settings "alice")))))

    (testing "changed language must have been saved for user"
      (logout)
      (change-language :en)
      (wait-visible *driver* {:tag :h1 :fn/text "Welcome to REMS"})
      (delete-cookies *driver*)
      (login-as "alice")
      (wait-visible *driver* {:tag :h1, :fn/text "Aineistoluettelo"}))

    (testing "changing language while logged in"
      (change-language :en)
      (wait-visible *driver* {:tag :h1 :fn/text "Catalogue"}))
    (is true))) ; avoid no assertions warning

;; TODO: driver is passed to scroll-and-click, maybe create higher level overload with shorter name too

(defn slurp-fields [selector]
  (->> (for [row (query-all *driver* [selector {:fn/has-class :row}])
             :let [k (get-element-text-el *driver* (child *driver* row {:tag :label}))
                   v (get-element-text-el *driver* (child *driver* row {:css ".form-control"}))]]
         [k (str/trim v)])
       (into {})))


(defn test-create-license []
  (with-postmortem *driver* {:dir reporting-dir}
    (go-to-admin-licenses)
    (scroll-and-click *driver* :create-license)
    (wait-visible *driver* {:tag :h1 :fn/text "Create license"})
    (select-option "Organization" "nbn")
    (scroll-and-click *driver* :licensetype-link)
    (fill-form-field "License name" "Test License" {:index 1})
    (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
    (fill-form-field "License name" "Testilisenssi" {:index 2})
    (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
    (screenshot *driver* (io/file reporting-dir "about-to-create-license.png"))
    (scroll-and-click *driver* :save)
    (wait-visible *driver* {:tag :h1 :fn/text "License"})
    (wait-page-loaded)
    (screenshot *driver* (io/file reporting-dir "created-license.png"))
    (is (str/includes? (get-element-text *driver* {:css ".alert-success"}) "Success"))
    (is (= {"Organization" "nbn"
            "Title (EN)" "Test License"
            "Title (FI)" "Testilisenssi"
            "Type" "link"
            "External link (EN)" "https://www.csc.fi/home"
            "External link (FI)" "https://www.csc.fi/etusivu"
            "Active" ""}
           (slurp-fields :license)))))

(defn test-create-resource []
  (with-postmortem *driver* {:dir reporting-dir}
    (go-to-admin-resources)
    (scroll-and-click *driver* :create-resource)
    (wait-visible *driver* {:tag :h1 :fn/text "Create resource"})
    (select-option "Organization" "nbn")
    (fill-form-field "Resource identifier" "browser-testing:1")
    (select-option "License" "Test License")
    (screenshot *driver* (io/file reporting-dir "about-to-create-resource.png"))
    (scroll-and-click *driver* :save)
    (wait-visible *driver* {:tag :h1 :fn/text "Resource"})
    (wait-page-loaded)
    (screenshot *driver* (io/file reporting-dir "created-resource.png"))
    (is (str/includes? (get-element-text *driver* {:css ".alert-success"}) "Success"))
    (is (= {"Organization" "nbn"
            "Resource" "browser-testing:1"
            "Active" ""}
           (slurp-fields :resource)))
    (is (= "License \"Test License\"" (get-element-text *driver* [:licenses {:class :license-title}])))))

(deftest test-create-catalogue-item
  (with-postmortem *driver* {:dir reporting-dir}
    (login-as "owner")
    (testing "create license"
      (test-create-license))
    (testing "create resource"
      (test-create-resource))
    (testing "create form")
    (testing "create workflow")
    (testing "create catalogue item")))
