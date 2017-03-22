(ns rems.test.form
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.form :as form]))

(def field #'form/field)

(deftest test-license-field
  (let [f (field {:type "license" :linktype "link" :linkcontent "ab.c" :title "Link to license"})
        [[_ attrs]] (hiccup-find [:input] f)
        [[_ target]] (hiccup-find [:a] f)]
    (is (= "checkbox" (:type attrs))
        "Checkbox exists for supported license type")
    (is (= "_blank" (:target target))
        "License with type link opens to a separate tab"))
  (let [f (field {:type "license" :linktype "attachment" :linkcontent "ab.c" :title "Link to license"})]
    (is (.contains (hiccup-text f) "Unsupported field ")
        "Unsupported license type gives a warning")))
