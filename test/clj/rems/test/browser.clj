(ns ^:browser rems.test.browser
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [etaoin.api :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config]
            [rems.db.test-data :as test-data]
            [rems.standalone]))

(def ^:dynamic *driver*
  "Current driver")

(def reporting-dir (doto (io/file "browsertest-errors")
                     (.mkdirs)))

(defn fixture-driver
  "Executes a test running a driver.
   Bounds a driver with the global *driver* variable."
  [f]
  (with-chrome-headless {} driver
    (binding [*driver* driver]
      (f))))

(defn fixture-standalone [f]
  (mount/start)
  (migrations/migrate ["reset"] (select-keys rems.config/env [:database-url]))
  (test-data/create-test-data!)
  (f)
  (mount/stop))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

(use-fixtures
  :once
  fixture-standalone)

;;; basic navigation

(def ^:private +test-url+ "http://localhost:3001/")

(defn login-as [username]
  (doto *driver*
    (set-window-size 1400 2000) ;; big enough to show the whole page without scrolling
    (go +test-url+)
    (screenshot (io/file reporting-dir "landing-page.png"))
    (click-visible {:class "login-btn"})
    (screenshot (io/file reporting-dir "login-page.png"))
    (click-visible [{:class "users"} {:tag :a, :fn/text username}])
    (wait-visible :logout)))

(defn- wait-page-loaded []
  (wait-invisible *driver* {:css ".fa-spinner"}))

(defn click-navigation-menu [link-text]
  (click-visible *driver* [:big-navbar {:tag :a, :fn/text link-text}]))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (wait-visible *driver* {:tag :h2, :fn/text "Catalogue"})
  (wait-page-loaded))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (wait-visible *driver* {:tag :h2, :fn/text "Applications"})
  (wait-page-loaded))

;;; catalogue page

(defn add-to-cart [resource-name]
  (click-visible *driver* [{:css "table.catalogue"}
                           {:fn/text resource-name}
                           {:xpath "./ancestor::tr"}
                           {:css "button.add-to-cart"}]))

(defn apply-for-resource [resource-name]
  (click-visible *driver* [{:css "table.cart"}
                           {:fn/text resource-name}
                           {:xpath "./ancestor::tr"}
                           {:css "button.apply-for-catalogue-items"}])
  (wait-visible *driver* {:tag :h2, :fn/text "Application"})
  (wait-page-loaded))

;;; application page

(defn fill-form-field [label text]
  (let [id (get-element-attr *driver* [:form
                                       {:tag :label, :fn/text label}]
                             :for)]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (fill-human *driver* {:id id} text)))

(defn accept-license [label]
  ;; XXX: assumes that the checkbox is unchecked
  (click-visible *driver* [:licenses
                           {:tag :a, :fn/text label}
                           {:xpath "./ancestor::div[@class='license']"}
                           {:css "input[type='checkbox']"}]))

(defn send-application []
  (click-visible *driver* :submit)
  (wait-has-class *driver* :apply-phase "completed"))

(defn get-application-id []
  (last (str/split (get-url *driver*) #"/")))

;; applications page

(defn get-application-summary [application-id]
  (let [row (query *driver* [{:css "table.applications"}
                             {:tag :td, :class "id", :fn/text application-id}
                             {:xpath "./ancestor::tr"}])]
    {:id (get-element-text-el *driver* (child *driver* row {:css ".id"}))
     :description (get-element-text-el *driver* (child *driver* row {:css ".description"}))
     :resource (get-element-text-el *driver* (child *driver* row {:css ".resource"}))
     :applicant (get-element-text-el *driver* (child *driver* row {:css ".applicant"}))
     :state (get-element-text-el *driver* (child *driver* row {:css ".state"}))}))

;;; tests

(deftest test-new-application
  (with-postmortem *driver* {:dir reporting-dir}
    (login-as "developer")

    (go-to-catalogue)
    (add-to-cart "ELFA Corpus, direct approval")
    (apply-for-resource "ELFA Corpus, direct approval")

    (fill-form-field "Project name" "Test name")
    (fill-form-field "Purpose of the project" "Test purpose")
    (accept-license "CC Attribution 4.0")
    (accept-license "General Terms of Use")
    (send-application)
    (is (= "State: Approved" (get-element-text *driver* :application-state)))

    (let [application-id (get-application-id)]
      (go-to-applications)
      (is (= {:id application-id
              :description ""
              :resource "ELFA Corpus, direct approval"
              :applicant "developer"
              :state "Approved"}
             (get-application-summary application-id))))))

(deftest test-guide-page
  (with-postmortem *driver* {:dir reporting-dir}
    (go *driver* (str +test-url+ "#/guide"))
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (query-all *driver* {:class :example}))))))
