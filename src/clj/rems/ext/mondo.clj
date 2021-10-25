(ns rems.ext.mondo
  "Loading and processing Mondo Disease Ontology.

  See https://github.com/monarch-initiative/mondo/"
  (:require [clojure.data.xml :as xml]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [medley.core :refer [find-first update-existing]]
            [rems.config]
            [rems.common.util :refer [build-index]]
            [rems.github :as github]))

(defn- strip-bom
  "Strips the byte order mark (BOM) (or whatever non-characters exist)
  from the start of the XML."
  [s]
  (-> s
      str/trim
      (str/replace-first #"^[\\W]+<" "<")))

(defn- mondo-class?
  "Is `x` an XML element representing a Mondo code?"
  [x]
  (and (= :Class (:tag x))
       (str/starts-with? (get-in x [:attrs :rdf/about] "")
                         "http://purl.obolibrary.org/obo/MONDO_")))

(defn- mondo-class-deprecated?
  "Is `x` an XML element representing something deprecated?"
  [x]
  (some? (some (comp #{:deprecated} :tag)
               (:content x))))

(defn- format-mondo-class
  "Takes the Mondo class XML element `x` and formats it for our internal use."
  [x]
  (let [id (-> x :attrs :rdf/about)
        label (->> x
                   :content
                   (find-first (comp #{:label} :tag))
                   :content
                   (str/join ""))]
    (assert (not (str/blank? id)))
    (assert (not (str/blank? label)) label)
    {:id id
     :label label}))

(defn- compressed-format
  "Takes `coll` of internal use Mondo codes and compresses
  them to a sequence sorted by `:id`. This is useful for storage in EDN."
  [coll]
  (let [skip-prefix (count "http://purl.obolibrary.org/obo/MONDO_")]
    (->> coll
         (mapv (fn [x] (update-existing x :id #(subs % skip-prefix))))
         (mapv (juxt :id :label))
         (sort-by :id))))

(def ^:private uninteresting-tags
  "The OWL file contains a lot of model that we are not interested in."
  #{:AnnotationProperty
    :ObjectProperty
    :Axiom
    :NamedIndividual
    :Restriction
    :Description})

(defn- parse-mondo
  "Parses a Mondo OWL ontology from XML string `s`.

  Returns a simplified, compressed format vector."
  [^String s]
  (with-open [reader (io/reader (java.io.ByteArrayInputStream. (.getBytes s "UTF-8")))]
    (let [root (xml/parse reader :validating false)]
      (->> root
           :content
           (remove string?)
           (remove (comp uninteresting-tags :tag))
           (filter mondo-class?)
           (remove mondo-class-deprecated?)
           (mapv format-mondo-class)
           compressed-format
           vec))))

(def ^:private supported-mondo-release-tag "the version of Mondo we support so far" "v2021-10-01")





(defn- load-codes
  "Load and index Mondo codes."
  []
  (->> (slurp "mondo.edn")
       edn/read-string
       (build-index {:keys [first]
                     :value-fn (fn [x] {:id (first x)
                                        :label (second x)})})))

(def ^:private code-by-id (atom nil))

(defn- get-codes
  "Return codes or a code by `id` with fallback to a default value for unknown codes.

  Loads the codes to the cache or empties it depending on if `:enable-duo` is set."
  [& [id]]
  (let [unknown-value {:label "unknown code"}]
    (if (:enable-duo rems.config/env)
      (do
        (when (nil? @code-by-id)
          (reset! code-by-id (load-codes)))
        (if (nil? id)
          (vals @code-by-id)
          (get @code-by-id id unknown-value)))

      (do
        (when (seq @code-by-id)
          (reset! code-by-id nil))
        (if (nil? id)
          []
          unknown-value)))))

(defn get-mondo-codes
  []
  (->> (get-codes)
       (sort-by :id)))

(defn- search-match [code ^String search-text]
  (let [text (str (:id code) " " (:label code))]
    (.contains text search-text)))

(deftest test-search-match
  (is (search-match {:id "0000004" :label "test code coder"} "test")))

(defn search-mondo-codes [search-text]
  (as-> (get-codes) codes
    (if (str/blank? search-text)
      codes
      (filterv #(search-match % search-text) codes))
    (sort-by :id codes)))

(defn join-mondo-code [duo-code]
  (update-existing duo-code
                   :restrictions
                   (fn [restrictions]
                     (for [restriction restrictions]
                       (if (= :MONDO (:type restriction))
                         (update-existing restriction
                                          :values
                                          (fn [values]
                                            (mapv (fn [value]
                                                    (get-codes (:id value)))
                                                  values)))
                         restriction)))))


(comment
  ;; Here is code to load the latest Mondo release
  ;; check it and potentially update application.
  ;; This is a manual process for the developers
  ;; as a new release may bring codes that need special handling.

  (def latest-release (github/fetch-releases-latest-asset "monarch-initiative/mondo" "mondo.owl"))
  (def latest-asset (-> latest-release :asset slurp strip-bom))

  ;; check to see if the new release is still the
  (assert (= supported-mondo-release-tag (:tag latest-release)))

  ;; save the codes to the file
  (spit "mondo.edn"
        (with-out-str
          (clojure.pprint/write (parse-mondo latest-asset)
                                :dispatch
                                clojure.pprint/code-dispatch))))
