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

;; test helpers

(defn login-as [username]
  (doto *driver*
    (set-window-size 1400 2000) ;; big enough to show the whole page without scrolling
    (go "http://localhost:3001")
    (screenshot "browsertest-errors/landing-page.png")
    (click-visible {:class "login-btn"})
    (screenshot "browsertest-errors/login-page.png")
    (click-visible [{:class "users"} {:tag :a, :fn/text username}])))

(defn click-navigation-menu [link-text]
  (doto *driver*
    (click-visible [:big-navbar {:tag :a, :fn/has-text link-text}])
    (wait-visible [{:tag :h2, :fn/has-text link-text}])))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (wait-has-text *driver* {:tag :h2} "Catalogue"))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (wait-has-text *driver* {:tag :h2} "Applications"))

;; now declare your tests

(deftest test-new-application
  (with-postmortem *driver* {:dir "browsertest-errors"}
    (login-as "developer")
    (go-to-catalogue)
    (doto *driver*
      (click-visible [{:class "rems-table catalogue"} {:class "btn btn-primary "}]) ; Click "Add to cart" on first item
      (click-visible [{:class "cart-item separator"} {:class "btn btn-primary"}]) ; Click "Apply"
      (wait-visible :field1)
      ; On application page
      ; Need to use fill-human, because human is so quick that the form
      ; drops characters here and there
      (fill-human :field1 "Test name")
      (fill-human :field2 "Test purpose")
      (click-visible {:name :license1}) ; Accept license
      (click-visible {:name :license2}) ; Accept terms
      (click-visible :submit)
      (wait-has-text :application-state "State: Approved"))
    (go-to-applications)

    (is (= "Deve Loper /"
           (get-element-text *driver* {:class "user-name"})))
    (is (= "ELFA Corpus, direct approval"
           (get-element-text *driver* {:data-th "Resource"})))
    (is (= "Approved"
           (get-element-text *driver* {:data-th "State"})))))
