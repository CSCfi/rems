(ns rems.ext.mondo
  "Loading and processing Mondo Disease Ontology.

  See https://github.com/monarch-initiative/mondo/"
  (:require [clojure.data.xml :as xml]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [medley.core :refer [find-first update-existing]]
            [mount.core :as mount]
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
  [s]
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

(mount/defstate code-by-id
  :start (when (:enable-duo rems.config/env)
           (load-codes)))


(defn get-mondo-codes
  []
  (->> code-by-id
       vals
       (sort-by :id)))

(defn join-mondo-codes
  "Join the Mondo codes under key `k` in `x`."
  [k x]
  (let [unknown-value {:id "unknown"
                       :label "unknown"}
        code-or-default (fn [mondo] (code-by-id (:id mondo) (merge unknown-value mondo)))]
    (update-in x [k :mondo/codes] (partial mapv code-or-default))))



(comment
  ;; Here is code to load the latest Mondo release
  ;; check it and potentially update application.
  ;; This is a manual process for the developers
  ;; as a new release may bring codes that need special handling.

  (def latest-release (github/fetch-releases-latest-asset "monarch-initiative/mondo" "mondo.owl"))

  ;; check to see if the new release is still the
  (assert (= supported-mondo-release-tag (:tag latest-release)))

  ;; save the codes to the file
  (->> latest-release
       :asset
       slurp
       strip-bom
       parse-mondo
       (spit "mondo.edn")))
