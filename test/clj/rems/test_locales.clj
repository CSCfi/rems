(ns rems.test-locales
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging]
            [rems.common.util :refer [recursive-keys]]
            [rems.locales :as locales]
            [rems.testing-util :refer [create-temp-dir]]
            [rems.util :refer [getx-in delete-directory-recursively]]
            [taoensso.tempura.impl :refer [compile-dictionary]])
  (:import (java.io FileNotFoundException)))

(deftest test-unused-translation-keys
  (let [unused-translation-keys #'rems.locales/unused-translation-keys]
    (is (= nil (unused-translation-keys {:a {:b "x" :c "x"}} {:a {:b "y"}})))
    (is (= (set [[:x] [:a :d]])
           (set (unused-translation-keys {:a {:b "x" :c "x"}}
                                         {:a {:b "y" :d "y"} :x "y"}))))))

(defn loc-en []
  (read-string (slurp (io/resource "translations/en.edn"))))

(defn loc-fi []
  (read-string (slurp (io/resource "translations/fi.edn"))))

(defn loc-sv []
  (read-string (slurp (io/resource "translations/sv.edn"))))

(deftest test-all-languages-defined
  (is (= (recursive-keys (loc-en))
         (recursive-keys (loc-fi)))
      "en matches fi")
  (is (= (recursive-keys (loc-en))
         (recursive-keys (loc-sv)))
      "en matches sv"))

(deftest test-extract-format-parameters
  (is (= #{} (locales/extract-format-parameters "hey you are 100% correct!")))
  (is (= #{"%3" "%5" "%7"} (locales/extract-format-parameters "user %3 has made %7 alterations in %5!"))))

(deftest test-format-parameters-match
  (testing "[:en vs :fi]"
    (let [en (loc-en)
          fi (loc-fi)]
      (doseq [k (recursive-keys en)] ;; we check that keys match separately
        (testing k
          (is (= (locales/extract-format-parameters (getx-in en (vec k)))
                 (locales/extract-format-parameters (getx-in fi (vec k)))))))))
  (testing "[:en vs :sv]"
    (let [en (loc-en)
          sv (loc-sv)]
      (doseq [k (recursive-keys en)]
        (testing k
          (is (= (locales/extract-format-parameters (getx-in en (vec k)))
                 (locales/extract-format-parameters (getx-in sv (vec k))))))))))

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
        (let [dictionary (->> (locales/load-translations {:languages [lang]
                                                          :translations-directory "translations/"})
                              lang
                              (compile-dictionary false))
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
  (let [log (atom [])
        ;; redeffing log* is hacky, could instead set *logger-factory* to a fake logger
        translations (with-redefs [clojure.tools.logging/log* (fn [_ _ _ msg] (swap! log conj (str msg)))]
                       (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./example-theme/theme.edn"}))]
    (testing "extra translations override translations"
      (is (= "CSC – Overridden in Extra Translations" (getx-in translations [:en :t :footer])))
      (is (= "Text %1" (getx-in translations [:en :t :create-license :license-text]))))
    (testing "extra translations don't override keys that are not defined in extras"
      (is (= "Active" (getx-in translations [:en :t :administration :active]))))
    (testing "warnings"
      (is (< 0 (count @log)))
      (testing "for unused key"
        (is (some #(.contains % ":unused-key") @log)
            (pr-str @log)))
      (testing "for extra format parameters"
        (is (some #(.contains % "%1") @log)
            (pr-str @log))))))

(deftest theme-path-given-no-extra-translations
  (testing "translations work with theme-path in config and no extra-translations"
    (let [translations (locales/load-translations {:languages [:en]
                                                   :translations-directory "translations/"
                                                   :theme-path "./foo/bar/theme.edn"})]
      (is (= "Display archived" (getx-in translations [:en :t :administration :display-archived]))))))
