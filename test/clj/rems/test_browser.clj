(ns ^:browser rems.test-browser
  "You need to run these tests in the :test profile to get the right :database-url and :port"
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [etaoin.api :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.common.util :refer [getcat-in]]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.standalone])
  (:import (java.net SocketException)))

(def ^:private +test-url+ "http://localhost:3001/")

(def ^:dynamic *driver*)

(def reporting-dir (doto (io/file "browsertest-errors")
                     (.mkdirs)))

(defn fixture-driver
  "Executes a test running a driver.
   Bounds a driver with the global *driver* variable."
  [f]
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
        (run)))))

(defn fixture-standalone [f]
  (mount/start)
  (migrations/migrate ["migrate"] (select-keys rems.config/env [:database-url]))
  (test-data/create-test-data!)
  (f)
  (migrations/migrate ["reset"] (select-keys rems.config/env [:database-url]))
  (mount/stop))

(defn smoke-test [f]
  (let [response (http/get (str +test-url+ "js/app.js"))]
    (assert (= 200 (:status response))
            (str "Failed to load app.js: " response))
    (f)))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

(use-fixtures
  :once
  fixture-standalone
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
    (go +test-url+)
    (screenshot (io/file reporting-dir "landing-page.png"))
    (scroll-and-click {:class "login-btn"})
    (screenshot (io/file reporting-dir "login-page.png"))
    (scroll-and-click [{:class "users"} {:tag :a, :fn/text username}])
    (wait-visible :logout)
    (screenshot (io/file reporting-dir "logged-in.png"))))

(defn logout []
  (click *driver* :logout))

(defn- wait-page-loaded []
  (wait-invisible *driver* {:css ".fa-spinner"}))

(defn click-navigation-menu [link-text]
  (scroll-and-click *driver* [:big-navbar {:tag :a, :fn/text link-text}]))

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

(defn fill-form-field [label text]
  (let [id (get-element-attr *driver* [:application-fields
                                       {:tag :label, :fn/has-text label}]
                             :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (fill-human *driver* {:id id} text)))

(defn set-date [label date]
  (let [id (get-element-attr *driver* [:application-fields
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
  (let [id (get-element-attr *driver* [:application-fields
                                       {:tag :label, :fn/has-text label}]
                             :for)]
    (fill *driver* {:id id} option)))

(defn field-visible? [label]
  (visible? *driver* [:application-fields
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

      (fill-form-field "Application title field" "Test name")
      (fill-form-field "Text field" "Test")
      (fill-form-field "Text area" "Test2")
      (set-date "Date field" "2050-01-02")
      (fill-form-field "Email field" "user@example.com")
      ;; leave attachment field empty
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
      (is (= "Applied" (get-element-text *driver* :application-state))))

    (let [application-id (get-application-id)]
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
                                                "x-rems-user-id" "handler"}}))]
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
                    ["attachment" ""]
                    ["option" "Option1"]
                    ["text" "Conditional"]
                    ["multiselect" "Option2 Option3"]
                    ["label" ""]
                    ["text" ""]
                    ["texta" ""]]
                   (for [field (getcat-in application [:application/forms :form/fields])]
                     ;; TODO could test other fields here too, e.g. title
                     [(:field/type field)
                      (:field/value field)])))))))))

(deftest test-guide-page
  (with-postmortem *driver* {:dir reporting-dir}
    (go *driver* (str +test-url+ "guide"))
    (wait-visible *driver* {:tag :h1 :fn/text "Component Guide"})
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (query-all *driver* {:class :example}))))))

(deftest test-language-change
  (with-postmortem *driver* {:dir reporting-dir}
    (testing "default language is English"
      (go *driver* +test-url+)
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
