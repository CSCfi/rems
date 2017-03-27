(ns rems.test.form
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.form :as form]
            [rems.test.tempura :refer [with-fake-tempura]]))

(def field #'form/field)

(deftest test-license-field
  (let [f (field {:type "license" :licensetype "link" :textcontent "ab.c" :title "Link to license"})
        [[_ attrs]] (hiccup-find [:input] f)
        [[_ target]] (hiccup-find [:a] f)]
    (is (= "checkbox" (:type attrs))
        "Checkbox exists for supported license type")
    (is (= "_blank" (:target target))
        "License with type link opens to a separate tab"))
  (let [f (field {:type "license" :licensetype "attachment" :textcontent "ab.c" :title "Link to license"})]
    (is (.contains (hiccup-text f) "Unsupported field ")
        "Unsupported license type gives a warning")))

(def validate #'form/validate)

(deftest test-validate
  (with-fake-tempura
    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional true
                             :value nil}
                            {:title "B"
                             :optional false
                             :value "xyz"}
                            {:title "C"
                             :optional false
                             :value "1"}]})))
    (let [res (validate
               {:items [{:title "A"
                         :optional true
                         :value nil}
                        {:title "B"
                         :optional false
                         :value ""}
                        {:title "C"
                         :optional false
                         :value nil}]})]
      (testing res
        (is (vector? res))
        (is (= 2 (count res)))
        (is (.contains (first res) "B"))
        (is (.contains (second res) "C"))))))
