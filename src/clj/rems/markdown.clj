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

(defn- apply-padding [coll]
  (let [max-len (apply max (map count coll))
        get-padding #(repeat (- max-len (count %)) " ")]
    (->> coll
         (mapv #(apply str % (get-padding %))))))

(defn- add-table-header-separator [coll]
  (let [make-separator (comp str/join #(repeat (count %) "-"))]
    (vec
     (concat (take 1 coll)
             (conj [] (mapv make-separator (first coll)))
             (drop 1 coll)))))

(defn markdown-table [{:keys [header rows row-fn]}]
  (let [transpose #(apply mapv vector %)
        format-rows #(->> (str/join " | " %)
                          (format "| %s |"))
        row-fn (or row-fn identity)
        cols (mapv str header)
        rows (vec (for [row rows]
                    (mapv str (row-fn row))))]
    (->> (into [cols] rows)
         transpose
         (mapv apply-padding) ; align column width
         transpose
         add-table-header-separator
         (mapv format-rows))))

(comment
  (doseq [row (markdown-table {:header ["handler count" "mean time" "lower-q 2.5%" "upper-q 97.5%"]
                               :rows [[1 0.049 0.039 0.067]
                                      [10 0.423 0.401 0.452]
                                      [50 2.098 1.910 2.294]
                                      [100 4.145 3.849 4.673]
                                      [200 8.539 8.025 9.374]]})]
    (println row)))
