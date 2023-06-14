(ns rems.markdown
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(defn- starts-clj-block? [line]
  (or (str/starts-with? (str/triml line) "```clj")
      (str/starts-with? (str/triml line) "```clojure")))

(defn- ends-clj-block? [line]
  (str/starts-with? (str/triml line) "```"))

(defn strip-to-clj-content
  "Strips every non-Clojure code line from the content in `lines`.

  Preserves the lines so that empty blank lines will be injected
  instead of removing them completely. This is to preserve line
  (line-seq )numbers in exceptions."
  [lines]
  (let [blank-line ""
        newline "\n"]
    (loop [lines lines
           clojure-code-block? false
           result-lines []]
      (let [line (first lines)]
        (cond (not line) ; eof
              (mapv #(str % newline) result-lines)

              (and (not clojure-code-block?)
                   (starts-clj-block? line))
              (recur (rest lines)
                     true
                     (conj result-lines blank-line))

              (and clojure-code-block?
                   (ends-clj-block? line))
              (recur (rest lines)
                     false
                     (conj result-lines blank-line))

              clojure-code-block?
              (recur (rest lines)
                     true
                     (conj result-lines line))

              :else
              (recur (rest lines)
                     false
                     (conj result-lines blank-line)))))))

(deftest test-strip-to-clj-content
  (is (= ["\n"] (strip-to-clj-content [""])))

  (is (= ["\n"
          "\n"
          "\n"]
         (strip-to-clj-content ["# title"
                                ""
                                "content"]))
      "produces empty lines for each stripped line")

  (is (= ["\n"
          "\n"
          "(+ 1\n"
          "   2\n"
          "   3)\n"
          "\n"
          "\n"]
         (strip-to-clj-content ["```clj"
                                ""
                                "(+ 1"
                                "   2"
                                "   3)"
                                ""
                                "```"]))
      "produces empty lines for each stripped line")

  (is (= ["\n"
          "\n"
          "\n"
          "(fun 1\n"
          "     2 (fan 3))\n"
          "\n"
          "\n"]
         (strip-to-clj-content ["# code"
                                ""
                                "```clj"
                                "(fun 1"
                                "     2 (fan 3))"
                                "```"
                                "the end"]))
      "keeps Clojure code intact"))
