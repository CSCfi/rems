(ns rems.ext.mondo
  "Loading and processing Mondo Disease Ontology.

  See https://github.com/monarch-initiative/mondo/"
  (:require [clojure.data.xml :as xml]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [medley.core :refer [find-first update-existing index-by]]
            [com.rpl.specter :refer [ALL filterer must transform]]
            [com.stuartsierra.dependency :as dep]
            [rems.config]
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
                   (str/join ""))
        parents (->> x
                     :content
                     (filter (comp #{:subClassOf} :tag))
                     (keep (comp :rdf/resource :attrs)))]
    (assert (not (str/blank? id)))
    (assert (not (str/blank? label)) label)
    {:id id
     :label label
     :parents parents}))

(defn- compressed-format
  "Takes `coll` of internal use Mondo codes and compresses
  them to a sequence sorted by `:id`. This is useful for storage in EDN."
  [coll]
  (let [skip-prefix (count "http://purl.obolibrary.org/obo/MONDO_")]
    (->> coll
         (mapv (fn [x] (-> x
                           (update-existing :id #(subs % skip-prefix))
                           (update-existing :parents (partial mapv #(subs % skip-prefix))))))
         (mapv (juxt :id :label :parents))
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

(def ^:private supported-mondo-release-tag
  "the version of Mondo we support so far"
  "v2022-09-06")

(def ^:private code-by-id (atom nil))
(def ^:private codes-dag (atom nil))

(defn- ensure-codes-are-loaded
  "Load and index Mondo codes, and generate directed acyclic graph
   for querying Mondo code hierarchy."
  []
  (when (or (nil? @code-by-id) (nil? @codes-dag))
    (let [codes (->> (slurp (io/resource "mondo.edn"))
                     edn/read-string
                     (mapv (fn [[id label parents]]
                             {:id id
                              :label label
                              :parents parents})))]
      (reset! code-by-id (->> codes
                              (mapv #(dissoc % :parents))
                              (index-by :id)))
      (reset! codes-dag (->> codes
                             (reduce (fn [g {:keys [id parents]}]
                                       (reduce #(dep/depend %1 id %2) g parents))
                                     (dep/graph)))))))

(defn- add-mondo-prefix [id]
  (str "MONDO:" id))

(defn- strip-mondo-prefix [id]
  (str/replace id #"^MONDO:" ""))

(defn- get-codes
  "Return codes or a code by `id` with fallback to a default value for unknown codes.

  Loads the codes to the cache or empties it depending on if `:enable-duo` is set."
  [& [id]]
  (let [id (some-> id strip-mondo-prefix)
        unknown-value {:id id
                       :label "unknown code"}]
    (if (:enable-duo rems.config/env)
      (do
        (ensure-codes-are-loaded)
        (if (nil? id)
          (vals @code-by-id)
          (get @code-by-id id unknown-value)))
      (do
        (reset! code-by-id nil)
        (if (nil? id)
          []
          unknown-value)))))

(defn get-mondo-codes
  []
  (->> (get-codes)
       (sort-by :id)
       (map #(update-existing % :id add-mondo-prefix))))

(defn get-mondo-parents
  [code]
  (if (:enable-duo rems.config/env)
    (do
      (ensure-codes-are-loaded)
      (->> code
           strip-mondo-prefix
           (dep/transitive-dependencies @codes-dag)
           (map add-mondo-prefix)
           set))
    (do
      (reset! codes-dag nil)
      #{})))

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
    (take 100 codes)
    (sort-by :id codes)
    (map #(update-existing % :id add-mondo-prefix) codes)))

(defn- update-mondo-prefix [restriction]
  (->> restriction
       (transform [(must :values) (filterer (comp some? :id)) ALL]
                  #(update-existing (get-codes (:id %)) :id add-mondo-prefix))))

(defn join-mondo-code [duo-code]
  (->> duo-code
       (transform [(must :restrictions) (filterer (comp #{:mondo} :type)) ALL]
                  update-mondo-prefix)))

(deftest test-join-mondo-code
  (with-redefs [rems.config/env {:enable-duo true}]
    (is (= nil (join-mondo-code nil)))
    (is (= {:restrictions []} (join-mondo-code {:restrictions []})))
    (is (= {:restrictions [{:type :mondo}]} (join-mondo-code {:restrictions [{:type :mondo}]})))
    (is (= {:restrictions [{:type :mondo :values []}]} (join-mondo-code {:restrictions [{:type :mondo :values []}]})))
    (is (= {:restrictions [{:type :mondo :values [{:value "MONDO:0000005"}]}]} (join-mondo-code {:restrictions [{:type :mondo :values [{:value "MONDO:0000005"}]}]}))
        "technically an invalid value but should not be changed")
    (is (= {:restrictions [{:type :mondo :values [{:id "MONDO:0000005" :label "alopecia, isolated"}]}]} (join-mondo-code {:restrictions [{:type :mondo :values [{:id "MONDO:0000005"}]}]})))))


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
  (spit "resources/mondo.edn"
        (with-out-str
          (clojure.pprint/write (parse-mondo latest-asset)
                                :dispatch
                                clojure.pprint/code-dispatch))))
