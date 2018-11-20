(ns ^:browser rems.test.browser
  (:require [clojure.test :refer :all]
            [etaoin.api :refer :all]
            [mount.core :as mount]
            [luminus-migrations.core :as migrations]
            [rems.standalone]
            [rems.config]
            [rems.db.test-data :as test-data]))

(def ^:dynamic *driver*
  "Current driver")

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

;;; test helpers

(defn login-as [username]
  (doto *driver*
    (set-window-size 1400 2000) ;; big enough to show the whole page without scrolling
    (go "http://localhost:3001")
    (screenshot "browsertest-errors/landing-page.png")
    (click-visible {:class "login-btn"})
    (screenshot "browsertest-errors/login-page.png")
    (click-visible [{:class "users"} {:tag :a, :fn/text username}])
    (wait-visible :logout)))

(defn click-navigation-menu [link-text]
  (click-visible *driver* [:big-navbar {:tag :a, :fn/text link-text}]))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (wait-visible *driver* {:tag :h2, :fn/text "Catalogue"}))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (wait-visible *driver* {:tag :h2, :fn/text "Applications"}))

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
                           {:css "button.apply-for-resource"}])
  (wait-visible *driver* {:tag :h2, :fn/text "Application"}))

;;; application page

(defn fill-form-field [label input]
  (let [id (get-element-attr *driver* [:form {:tag :label, :fn/text label}] :for)]
    ;; Need to use `fill-human`, because `fill` is so quick that the form
    ;; drops characters here and there
    (fill-human *driver* {:id id} input)))

;;; now declare your tests

(deftest test-new-application
  (with-postmortem *driver* {:dir "browsertest-errors"}
    (login-as "developer")

    (go-to-catalogue)
    (add-to-cart "ELFA Corpus, direct approval")
    (apply-for-resource "ELFA Corpus, direct approval")

    ; On application page
    (fill-form-field "Project name" "Test name")
    (fill-form-field "Purpose of the project" "Test purpose")
    (doto *driver*
      (click-visible {:name :license1}) ; Accept license
      (click-visible {:name :license2}) ; Accept terms
      (click-visible :submit)
      (wait-has-text :application-state "State: Approved"))

    (go-to-applications)
    (is (= "ELFA Corpus, direct approval"
           (get-element-text *driver* {:data-th "Resource"})))
    (is (= "Approved"
           (get-element-text *driver* {:data-th "State"})))))
