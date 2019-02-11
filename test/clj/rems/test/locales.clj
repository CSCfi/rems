(ns rems.test.locales
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            clojure.string
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.context :as context]
            [rems.locales :as locales]
            [rems.test.testing :refer [create-temp-dir delete-recursively]]
            [rems.text :refer [with-language]]
            [taoensso.tempura :as tempura])
  (:import (java.io FileNotFoundException)))

(def loc-en (read-string (slurp (io/resource "translations/en.edn"))))

(def loc-fi (read-string (slurp (io/resource "translations/fi.edn"))))

(defn map-structure
  "Recurse into map m and replace all leaves with true."
  [m]
  (let [transform (fn [v] (if (map? v) (map-structure v) true))]
    (reduce-kv (fn [m k v] (assoc m k (transform v))) {} m)))

(deftest test-all-languages-defined
  (is (= (map-structure loc-en)
         (map-structure loc-fi))))

(deftest all-translation-keywords-used-in-source-defined
  (let [all-tokens (->> (sh/sh "git" "grep" "-Iho" ":t\\.[-a-z.]*/[-a-z.]\\+")
                        :out
                        clojure.string/split-lines
                        (map read-string)
                        set)
        tr-config {:dict (locales/load-translations {:languages [:en]})}
        tr (partial tempura/tr tr-config [:en])]
    (doseq [token all-tokens]
      (testing token
        (is (tr [token]))))))

(deftest load-translations-test
  (testing "loads internal translations"
    (let [translations (locales/load-translations {:languages [:en :fi]})]
      (is (= [:en :fi] (sort (keys translations))))
      (is (not (empty? (:en translations))))
      (is (not (empty? (:fi translations))))))

  (testing "loads external translations"
    (let [translations-dir (create-temp-dir)
          config {:translations-directory translations-dir
                  :languages [:xx]}
          translation {:some-key "some val"}]
      (try
        (spit (io/file translations-dir "xx.edn")
              (pr-str translation))
        (is (= translation (:xx (locales/load-translations config))))
        (finally
          (delete-recursively translations-dir)))))

  (testing "loads translations only for listed languages"
    (is (= [:en] (keys (locales/load-translations {:languages [:en]}))))
    (is (= [:fi] (keys (locales/load-translations {:languages [:fi]})))))

  (testing "missing translations is an error"
    (is (thrown-with-msg? FileNotFoundException #"^\Qtranslations for :xx language could not be found in some-dir/xx.edn file or translations/xx.edn resource\E$"
                          (locales/load-translations {:translations-directory "some-dir"
                                                      :languages [:xx]})))
    (is (thrown-with-msg? FileNotFoundException #"^\Qtranslations for :xx language could not be found in translations/xx.edn resource and :translations-directory was not set\E$"
                          (locales/load-translations {:languages [:xx]})))))
