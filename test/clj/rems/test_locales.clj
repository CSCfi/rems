(ns rems.test-locales
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging]
            [clojure.tools.logging.test :as log-test]
            [rems.common.util :refer [getx-in recursive-keys]]
            [rems.locales :as locales]
            [rems.tempura]
            [rems.testing-util :refer [create-temp-dir]]
            [rems.util :refer [delete-directory-recursively]]
            [taoensso.tempura.impl])
  (:import (java.io FileNotFoundException)))

(defn loc-da []
  (read-string (slurp (io/resource "translations/da.edn"))))

(defn loc-en []
  (read-string (slurp (io/resource "translations/en.edn"))))

(defn loc-fi []
  (read-string (slurp (io/resource "translations/fi.edn"))))

(defn loc-sv []
  (read-string (slurp (io/resource "translations/sv.edn"))))

(deftest test-all-languages-defined
  (is (= (recursive-keys (loc-en))
         (recursive-keys (loc-da)))
      "en matches da")
  (is (= (recursive-keys (loc-en))
         (recursive-keys (loc-fi)))
      "en matches fi")
  (is (= (recursive-keys (loc-en))
         (recursive-keys (loc-sv)))
      "en matches sv"))

(defn- translation-keywords-in-use []
  ;; git grep would be nice, but circleci's git grep doesn't have -o
  ;; --include is needed to exclude editor backup files etc.
  (let [grep (sh/sh "grep" "-ERho" "--include=*.clj[cs]" "--include=*.clj" ":t[./][-a-z0-9./]+" "src")]
    (assert (= 0 (:exit grep))
            (pr-str grep))
    (->> grep
         :out
         str/split-lines
         (map read-string)
         set)))

(deftest test-translation-keywords-in-use
  (let [keys-in-source (set (translation-keywords-in-use))]
    (assert (seq keys-in-source))
    (doseq [lang [:en :fi :sv]]
      (testing lang
        (let [dictionary (-> (locales/load-translations {:languages [lang]
                                                         :translations-directory "translations/"})
                             lang
                             taoensso.tempura.impl/compile-dictionary)
              keys-in-dictionary (set (keys dictionary))]
          (assert (seq keys-in-dictionary))
          (testing "dictionary is missing translations"
            (is (empty? (sort (set/difference keys-in-source keys-in-dictionary)))))
          (testing "dictionary has unused translations"
            (is (empty? (sort (set/difference keys-in-dictionary keys-in-source))))))))))

(deftest load-translations-test
  (testing "loads internal translations"
    (let [translations (locales/load-translations {:languages [:en :fi]
                                                   :translations-directory "translations/"})]
      (is (= [:en :fi] (sort (keys translations))))
      (is (seq (:en translations)))
      (is (seq (:fi translations)))))

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
          (delete-directory-recursively translations-dir)))))

  (testing "loads translations only for listed languages"
    (is (= [:en] (keys (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"}))))
    (is (= [:fi] (keys (locales/load-translations {:languages [:fi]
                                                   :translations-directory "translations/"})))))

  (testing "missing translations-directory in config is an error"
    (is (thrown-with-msg? RuntimeException #"^\Q:translations-directory was not set in config\E$"
                          (locales/load-translations {:languages [:xx]}))))

  (testing "missing translations is an error"
    (is (thrown-with-msg? FileNotFoundException #"^translations could not be found in file or resource \"some-dir/xx.edn\"$"
                          (locales/load-translations {:translations-directory "some-dir/"
                                                      :languages [:xx]})))))

(deftest override-translations-with-extra-translations
  (log-test/with-log
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./test-data/test-theme/theme.edn"})]
      (testing "extra translations override translations"
        (is (= "CSC – Overridden in Extra Translations" (getx-in translations [:en :t :footer]))))
      (testing "extra translations don't override keys that are not defined in extras"
        (is (= "Active" (getx-in translations [:en :t :administration :active]))))
      (testing "warnings from unused translation keys"
        (is (log-test/logged? "rems.locales"
                              :warn
                              "Unused translation keys defined in ./test-data/test-theme/extra-translations/en.edn : #{:t/unused-key}")
            {:log (log-test/the-log)})))))

(deftest theme-path-given-no-extra-translations
  (testing "translations work with theme-path in config and no extra-translations"
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./foo/bar/theme.edn"})]
      (is (= "Display archived" (getx-in translations [:en :t :administration :display-archived]))))))
