(ns ^:browser rems.test-browser
  "REMS Browser tests.

  For the test database, you need to run these tests in the :test profile to get the right :database-url and :port.

  For development development tests, you can run against a running instance with:

  (init-driver! :chrome \"http://localhost:3000/\" :development)

  NB: While adding more test helpers, please put the `driver` argument as first to match etaoin and enable `doto`."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [select ALL]]
            [etaoin.api :as et]
            [medley.core :refer [assoc-some]]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.db.user-settings :as user-settings]
            [rems.standalone]
            [rems.test-browser-util :refer :all])
  (:import (java.net SocketException)))

(comment ; convenience for development testing
  (init-driver! :chrome "http://localhost:3000/" :development))

(use-fixtures :each fixture-driver)

(use-fixtures :once test-dev-or-standalone-fixture)

;;; common functionality

(defn login-as [driver username]
  (doto driver
    (et/set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
    (et/go (get-url))
    (et/screenshot (io/file reporting-dir "landing-page.png"))
    (scroll-and-click {:css ".login-btn"})
    (et/screenshot (io/file reporting-dir "login-page.png"))
    (scroll-and-click [{:class "users"} {:tag :a :fn/text username}])
    (et/wait-visible :logout)
    (et/screenshot (io/file reporting-dir "logged-in.png"))))

(defn logout [driver]
  (doto driver
    (scroll-and-click :logout)
    (et/wait-visible {:css ".login-component"})))

(defn click-navigation-menu [driver link-text]
  (scroll-and-click driver [:big-navbar {:tag :a :fn/text link-text}]))

(defn click-administration-menu [driver link-text]
  (scroll-and-click driver [:administration-menu {:tag :a :fn/text link-text}]))

(defn go-to-catalogue [driver]
  (doto driver
    (click-navigation-menu "Catalogue")
    (et/wait-visible {:tag :h1 :fn/text "Catalogue"})
    (wait-page-loaded)
    (et/screenshot (io/file reporting-dir "catalogue-page.png"))))

(defn go-to-applications [driver]
  (doto driver
    (click-navigation-menu "Applications")
    (et/wait-visible {:tag :h1 :fn/text "Applications"})
    (wait-page-loaded)
    (et/screenshot (io/file reporting-dir "applications-page.png"))))

(defn go-to-admin-licenses [driver]
  (doto driver
    (click-administration-menu "Licenses")
    (et/wait-visible {:tag :h1 :fn/text "Licenses"})
    (wait-page-loaded)
    (et/screenshot (io/file reporting-dir "administration-licenses-page.png"))))

(defn go-to-admin-resources [driver]
  (doto driver
    (click-administration-menu "Resources")
    (et/wait-visible {:tag :h1 :fn/text "Resources"})
    (wait-page-loaded)
    (et/screenshot (io/file reporting-dir "administration-resources-page.png"))))

(defn change-language [driver language]
  (scroll-and-click driver [{:css ".language-switcher"} {:fn/text (.toUpperCase (name language))}]))



;;; catalogue page

(defn add-to-cart [driver resource-name]
  (scroll-and-click driver [{:css "table.catalogue"}
                            {:fn/text resource-name}
                            {:xpath "./ancestor::tr"}
                            {:css ".add-to-cart"}]))

(defn apply-for-resource [driver resource-name]
  (doto driver
    (scroll-and-click [{:css "table.cart"}
                       {:fn/text resource-name}
                       {:xpath "./ancestor::tr"}
                       {:css ".apply-for-catalogue-items"}])
    (et/wait-visible  {:tag :h1 :fn/has-text "Application"})
    (wait-page-loaded)
    (et/screenshot  (io/file reporting-dir "application-page.png"))))



;;; application page

(defn fill-form-field
  "Fills a form field named by `label` with `text`.

  Optionally give `:index` when several items match. It starts from 1."
  [driver label text & [opts]]
  (assert (> (:index opts 1) 0) "indexing starts at 1") ; xpath uses 1, let's keep the convention though we don't use xpath here because it will likely not work

  (let [el (nth (et/query-all driver [{:css ".fields"}
                                      {:tag :label :fn/has-text label}])
                (dec (:index opts 1)))
        id (et/get-element-attr-el driver el :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (et/fill-human driver {:id id} text)))

(defn set-date [driver label date]
  (let [id (et/get-element-attr driver [{:css ".fields"}
                                        {:tag :label :fn/text label}]
                                :for)]
    ;; XXX: The date format depends on operating system settings and is unaffected by browser locale,
    ;;      so we cannot reliably know the date format to type into the date field and anyways WebDriver
    ;;      support for date fields seems buggy. Changing the field with JavaScript is more reliable.
    (et/js-execute driver
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

(defn select-option [driver label option]
  (let [id (et/get-element-attr driver [{:css ".fields"}
                                        {:tag :label :fn/has-text label}]
                                :for)]
    (et/fill driver {:id id} (str option "\n"))))

(defn accept-licenses [driver]
  (doto driver
    (scroll-and-click :accept-licenses-button)
    (et/wait-visible :has-accepted-licenses)))

(defn send-application [driver]
  (doto driver
    (scroll-and-click :submit)
    (et/wait-visible :status-success)
    (et/wait-has-class :apply-phase "completed")))

(defn get-application-id [driver]
  (last (str/split (et/get-url driver) #"/")))

(defn get-attachments
  ([driver]
   (get-attachments driver {:css "a.attachment-link"}))
  ([driver selector]
   (mapv (partial et/get-element-text-el driver) (et/query-all driver selector))))



;; applications page

(defn get-application-summary [driver application-id]
  (let [row (et/query driver [{:css "table.my-applications"}
                              {:tag :tr :data-row application-id}])]
    {:id application-id
     :description (et/get-element-text-el driver (et/child driver row {:css ".description"}))
     :resource (et/get-element-text-el driver (et/child driver row {:css ".resource"}))
     :state (et/get-element-text-el driver (et/child driver row {:css ".state"}))}))

;;; tests

(deftest test-new-application
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (login-as driver "alice")

      (testing "create application"
        (doto driver
          (go-to-catalogue)
          (add-to-cart "Default workflow")
          (apply-for-resource "Default workflow"))

        (let [application-id (get-application-id driver)
              application (:body
                           (http/get (str (get-url) "/api/applications/" application-id)
                                     {:as :json
                                      :headers {"x-rems-api-key" "42"
                                                "x-rems-user-id" "handler"}}))
              form-id (get-in application [:application/forms 0 :form/id])
              description-field-id (get-in application [:application/forms 0 :form/fields 1 :field/id])
              description-field-selector (keyword (str "form-" form-id "-field-" description-field-id))
              attachment-field (get-in application [:application/forms 0 :form/fields 7])
              attachment-field-selector (keyword (str "form-" form-id "-field-" (:field/id attachment-field) "-input"))]
          (is (= "attachment" (:field/type attachment-field))) ;; sanity check

          (doto driver
            (fill-form-field "Application title field" "Test name")
            (fill-form-field "Text field" "Test")
            (fill-form-field "Text area" "Test2")
            (set-date "Date field" "2050-01-02")
            (fill-form-field "Email field" "user@example.com")
            (et/upload-file attachment-field-selector "test-data/test.txt")
            (wait-predicate #(= ["test.txt"] (get-attachments driver))))

          (is (not (field-visible? driver "Conditional field"))
              "Conditional field is not visible before selecting option")

          (doto driver
            (select-option "Option list" "First option")
            (wait-predicate #(field-visible? driver "Conditional field"))
            (fill-form-field "Conditional field" "Conditional")
            ;; pick two options for the multi-select field:
            (check-box "Option2")
            (check-box "Option3")
            ;; leave "Text field with max length" empty
            ;; leave "Text are with max length" empty

            (accept-licenses)
            (send-application))

          (is (= "Applied" (et/get-element-text driver :application-state)))

          (testing "check a field answer"
            (is (= "Test name" (et/get-element-text driver description-field-selector))))

          (testing "see application on applications page"
            (go-to-applications driver)
            (let [summary (get-application-summary driver application-id)]
              (is (= "Default workflow" (:resource summary)))
              (is (= "Applied" (:state summary)))
              ;; don't bother trying to predict the external id:
              (is (.contains (:description summary) "Test name"))))

          (testing "fetch application from API"
            (let [application (:body
                               (http/get (str (get-url) "/api/applications/" application-id)
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
                (scroll-and-click driver [{:css "table.my-applications"}
                                          {:tag :tr :data-row application-id}
                                          {:css ".btn-primary"}])
                (et/wait-visible driver {:tag :h1 :fn/has-text "Application"})
                (wait-page-loaded driver)
                (testing "check a field answer"
                  (is (= "Test name" (et/get-element-text driver description-field-selector))))))))))))

(deftest test-handling
  (let [driver (get-driver)
        applicant "alice"
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
    (et/with-postmortem driver {:dir reporting-dir}
      (login-as driver handler)
      (testing "handler should see todos on logging in"
        (et/wait-visible driver :todo-applications))
      (testing "handler should see description of application"
        (et/wait-visible driver {:class :application-description :fn/text "test-handling"}))
      (let [app-button {:tag :a :href (str "/application/" application-id)}]
        (testing "handler should see view button for application"
          (et/wait-visible driver app-button))
        (scroll-and-click driver app-button))
      (testing "handler should see application after clicking on View"
        (et/wait-visible driver {:tag :h1 :fn/has-text "test-handling"}))
      (testing "open the approve form"
        (scroll-and-click driver :approve-reject-action-button))
      (testing "add a comment and two attachments"
        (doto driver
          (et/wait-visible :comment-approve-reject)
          (et/fill-human :comment-approve-reject "this is a comment")
          (et/upload-file :upload-approve-reject-input "test-data/test.txt")
          (et/wait-visible [{:css "a.attachment-link"}])
          (et/upload-file :upload-approve-reject-input "test-data/test-fi.txt")
          (wait-predicate #(= ["test.txt" "test-fi.txt"]
                              (get-attachments driver)))))
      (testing "add and remove a third attachment"
        (et/upload-file driver :upload-approve-reject-input "resources/public/img/rems_logo_en.png")
        (et/wait-predicate #(= ["test.txt" "test-fi.txt" "rems_logo_en.png"]
                               (get-attachments driver)))
        (let [buttons (et/query-all driver {:css "button.remove-attachment-approve-reject"})]
          (et/click-el driver (last buttons)))
        (et/wait-predicate #(= ["test.txt" "test-fi.txt"]
                               (get-attachments driver))))
      (testing "approve"
        (scroll-and-click driver :approve)
        (et/wait-predicate #(= "Approved" (et/get-element-text driver :application-state))))
      (testing "attachments visible in eventlog"
        (is (= ["test.txt" "test-fi.txt"]
               (get-attachments driver {:css "div.event a.attachment-link"})))))))

(deftest test-guide-page
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (et/go driver (str (get-url) "guide"))
      (et/wait-visible driver {:tag :h1 :fn/text "Component Guide"})
      ;; if there is a js exception, nothing renders, so let's check
      ;; that we have lots of examples in the dom:
      (is (< 60 (count (et/query-all driver {:class :example})))))))

(deftest test-language-change
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (testing "default language is English"
        (doto driver
          (et/go (get-url))
          (et/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (login-as "alice")
          (et/wait-visible {:tag :h1 :fn/text "Catalogue"})
          (wait-page-loaded)))

      (testing "changing language while logged out"
        (doto driver
          (logout)
          (et/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (change-language :fi)
          (et/wait-visible {:tag :h1 :fn/text "Tervetuloa REMSiin"})))

      (testing "changed language must persist after login"
        (doto driver
          (login-as "alice")
          (et/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"})
          (wait-page-loaded)))

      (testing "wait for language change to show in the db"
        (wait-predicate driver #(= :fi (:language (user-settings/get-user-settings "alice")))))

      (testing "changed language must have been saved for user"
        (doto driver
          (logout)
          (change-language :en)
          (et/wait-visible {:tag :h1 :fn/text "Welcome to REMS"})
          (et/delete-cookies)
          (login-as "alice")
          (et/wait-visible {:tag :h1 :fn/text "Aineistoluettelo"})))

      (testing "changing language while logged i"
        (change-language driver :en)
        (et/wait-visible driver {:tag :h1 :fn/text "Catalogue"}))
      (is true)))) ; avoid no assertions warning

;; TODO: driver is passed to scroll-and-click, maybe create higher level overload with shorter name too

(defn slurp-fields [driver selector]
  (->> (for [row (et/query-all driver [selector {:fn/has-class :row}])
             :let [k (et/get-element-text-el driver (et/child driver row {:tag :label}))
                   v (et/get-element-text-el driver (et/child driver row {:css ".form-control"}))]]
         [k (str/trim v)])
       (into {})))


(defn test-create-license []
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (doto driver
        (go-to-admin-licenses)
        (scroll-and-click  :create-license)
        (et/wait-visible  {:tag :h1 :fn/text "Create license"})
        (select-option  "Organization" "nbn")
        (scroll-and-click :licensetype-link)
        (fill-form-field "License name" (str (:license-name @test-context) " EN") {:index 1})
        (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
        (fill-form-field "License name" (str (:license-name @test-context) " FI") {:index 2})
        (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
        (fill-form-field "License name" (str (:license-name @test-context) " SV") {:index 3})
        (fill-form-field "License link" "https://www.csc.fi/home" {:index 3})
        (et/screenshot (io/file reporting-dir "about-to-create-license.png"))
        (scroll-and-click :save)
        (et/wait-visible {:tag :h1 :fn/text "License"})
        (wait-page-loaded)
        (et/screenshot (io/file reporting-dir "created-license.png")))
      (is (str/includes? (et/get-element-text driver {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "nbn"
              "Title (EN)" (str (:license-name @test-context) " EN")
              "Title (FI)" (str (:license-name @test-context) " FI")
              "Title (SV)" (str (:license-name @test-context) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" ""}
             (slurp-fields driver :license))))))

(defn test-create-resource []
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (doto driver
        (go-to-admin-resources)
        (scroll-and-click :create-resource)
        (et/wait-visible {:tag :h1 :fn/text "Create resource"})
        (select-option "Organization" "nbn")
        (fill-form-field "Resource identifier" (:resid @test-context))
        (select-option "License" (str (:license-name @test-context) " EN"))
        (et/screenshot (io/file reporting-dir "about-to-create-resource.png"))
        (scroll-and-click :save)
        (et/wait-visible {:tag :h1 :fn/text "Resource"})
        (wait-page-loaded)
        (et/screenshot (io/file reporting-dir "created-resource.png")))
      (is (str/includes? (et/get-element-text driver {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "nbn"
              "Resource" (:resid @test-context)
              "Active" ""}
             (slurp-fields driver :resource)))
      (is (= (str "License \"" (:license-name @test-context) " EN\"")
             (et/get-element-text driver [:licenses {:class :license-title}]))))))

(deftest test-create-catalogue-item
  (let [driver (get-driver)]
    (et/with-postmortem driver {:dir reporting-dir}
      (login-as driver "owner")
      (swap! test-context assoc
             :license-name (str "Browser Test License " (get-seed))
             :resid (str "browser.testing.resource/" (get-seed)))
      (testing "create license"
        (test-create-license))
      (testing "create resource"
        (test-create-resource))
      (testing "create form") ; TODO
      (testing "create workflow") ; TODO
      (testing "create catalogue item")))) ; TODO
