(ns rems.test.browser
  (:require [clojure.test :refer :all]
            [etaoin.api :refer :all]))

(def ^:dynamic
  *driver*
  "Current driver")

(defn fixture-driver
  "Executes a test running a driver.
   Bounds a driver with the global *driver* variable."
  [f]
  (with-chrome {} driver
    (binding [*driver* driver]
      (f))))

(use-fixtures
  :each ;; start and stop driver for each test
  fixture-driver)

;; now declare your tests

(deftest ^:browser
  test-new-application
  (doto *driver*
    (go "http://localhost:3000")
    (click-visible {:class :login-btn}) ; Get login choices
    (click-visible {:class :user}) ; Choose first, "developer"
    (click-visible {:class "nav-item nav-link"}) ; Go to catalogue
    (click-visible [{:class "rems-table catalogue"} {:class "btn btn-primary "}]) ; Click "Add to cart" on first item
    (click-visible [{:class "cart-item separator"} {:class "btn btn-primary"}]) ; Click "Apply"
    (wait-visible :field1)
    ; On application page
    (fill-human :field1 "Test name")
    (fill-human :field2 "Test purpose")
    (click-visible {:name :license1}) ; Accept license
    (click-visible {:name :license2}) ; Accept terms
    (click-visible :submit)
    (click-visible {:class "nav-item nav-link active"}))
  (is (= "Deve Loper /"
         (get-element-text *driver* {:class :user-name})))
  (is (= "ELFA Corpus, direct approval"
         (get-element-text *driver* {:data-th :Resource})))
  (is (= "Approved"
         (get-element-text *driver* {:data-th :State}))))
