(ns rems.test-locales
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            clojure.string
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.context :as context]
            [rems.locales :as locales]
            [rems.util :refer [getx-in]]
            [rems.testing-util :refer [create-temp-dir delete-recursively]]
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
  ;; git grep would be nice, but circleci's git grep doesn't have -o
  ;; --include is needed to exclude editor backup files etc.
  (let [grep (sh/sh "grep" "-Rho" "--include=*.clj[cs]" "--include=*.clj" ":t\\.[-a-z.]*/[-a-z.]\\+" "src")]
    (assert (= 0 (:exit grep))
            (pr-str grep))
    (let [all-tokens (->> grep
                          :out
                          clojure.string/split-lines
                          (map read-string)
                          set)
          tr-config {:dict (locales/load-translations {:languages [:en]
                                                       :translations-directory "translations/"})}
          tr (partial tempura/tr tr-config [:en])]
      (doseq [token all-tokens]
        (testing token
          (is (tr [token])))))))

(deftest load-translations-test
  (testing "loads internal translations"
    (let [translations (locales/load-translations {:languages [:en :fi]
                                                   :translations-directory "translations/"})]
      (is (= [:en :fi] (sort (keys translations))))
      (is (not (empty? (:en translations))))
      (is (not (empty? (:fi translations))))))

  (testing "loads external translations"
    (let [translations-dir (create-temp-dir)
          config {:translations-directory translations-dir
                  :extra-translations-directory nil
                  :languages [:xx]}
          translation {:some-key "some val"}]
      (try
        (spit (io/file translations-dir "xx.edn")
              (pr-str translation))
        (is (= translation (:xx (locales/load-translations config))))
        (finally
          (delete-recursively translations-dir)))))

  (testing "loads translations only for listed languages"
    (is (= [:en] (keys (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"}))))
    (is (= [:fi] (keys (locales/load-translations {:languages [:fi]
                                                   :translations-directory "translations/"})))))

  (testing "missing translations-directory in config is an error"
    (is (thrown-with-msg? RuntimeException #"^\Q:translations-directory was not set in config\E$"
                          (locales/load-translations {:languages [:xx]}))))

  (testing "missing translations is an error"
    (is (thrown-with-msg? FileNotFoundException #"^\Qtranslations could not be found in some-dir/xx.edn file or some-dir/xx.edn resource\E$"
                          (locales/load-translations {:translations-directory "some-dir/"
                                                      :languages [:xx]})))))

(deftest override-translations-with-extra-translations
  (testing "extra translations override translations"
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./example-theme/theme.edn"})]
      (is (= "Catalogue" (getx-in translations [:en :t :administration :catalogue-items])))
      (is (= "Members" (getx-in translations [:en :t :actions :applicant])))))
  (testing "extra translations don't override keys that are not defined in extras"
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "example-theme/theme.edn"})]
      (is (= "Active" (getx-in translations [:en :t :administration :active]))))))

(deftest theme-path-given-no-extra-translations
  (testing "translations work with theme-path in config and no extra-translations"
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./foo/bar/theme.edn"})]
      (is (= "Catalogue items" (getx-in translations [:en :t :administration :catalogue-items]))))))
