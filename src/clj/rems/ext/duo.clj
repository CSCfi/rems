(ns rems.ext.duo
  (:require [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :refer [difference]]
            [medley.core :refer [update-existing]]
            [rems.common.util :refer [index-by]]
            [rems.config]
            [rems.github :as github]
            [rems.util :refer [read-zip-entries]]
            [schema.coerce :as coerce]
            [schema.core :as s]))

;; The CSV format has English texts only
(s/defschema DuoCodeCsv
  {:id s/Str
   (s/optional-key :shorthand) (s/maybe s/Str)
   :label s/Str
   :description s/Str})

(def ^:private coerce-DuoCodeCsv
  (coerce/coercer! DuoCodeCsv coerce/string-coercion-matcher))

(defn- rows->maps
  "Transform CSV `rows` into maps with the keys from the first header row."
  [rows]
  (mapv zipmap
        (->> (first rows) ; header
             (map keyword)
             repeat)
        (rest rows)))

(defn fetch-duo-releases-latest
  "Fetches the latest DUO codes from the GitHub repository.

  Returns the unpacked formatted result."
  []
  (let [{:keys [tag zip]} (github/fetch-releases-latest "EBISPOT/DUO")
        csv-bytes (-> zip (java.util.zip.ZipInputStream.) (read-zip-entries #".*/duo.csv$") first :bytes)]
    (with-open [reader (io/reader csv-bytes)]
      (let [codes (for [duo (->> reader csv/read-csv rows->maps)]
                    (-> duo
                        coerce-DuoCodeCsv
                        (update-existing :label (fn [en] {:en en}))
                        (update-existing :description (fn [en] {:en en}))))]
        {:tag tag
         :codes codes}))))

(def supported-duo-release-tag "v2021-02-23") ; the version we support so far

(def simple-codes
  "Codes that can be added to a resource without any additional questions."
  #{"DUO:0000004" "DUO:0000006" "DUO:0000011" "DUO:0000015" "DUO:0000016" "DUO:0000018" "DUO:0000019" "DUO:0000021" "DUO:0000029" "DUO:0000042" "DUO:0000043" "DUO:0000044" "DUO:0000045" "DUO:0000046"})

(def complex-codes
  "Codes that require specific handling or an additional question."
  {"DUO:0000007" {:type :MONDO}
   "DUO:0000012" {:type :topic}
   "DUO:0000020" {:type :collaboration}
   "DUO:0000022" {:type :location}
   "DUO:0000024" {:type :date}
   "DUO:0000025" {:type :months}
   "DUO:0000026" {:type :users}
   "DUO:0000027" {:type :project}
   "DUO:0000028" {:type :institute}})

(def abstract-codes #{"DUO:0000001" "DUO:0000017"}) ; not concrete types can't be used as tag

(defn- enrich-duo-code
  "Adds convenience attributes to DUO codes."
  [duo]
  (merge duo
         (when (contains? simple-codes (:id duo)) {:simple? true})))

(defn load-codes []
  (when (:enable-duo rems.config/env)
    (->> (slurp "duo.edn")
         edn/read-string
         (index-by [:id]))))

(def ^:private code-by-id (delay (load-codes)))

(defn get-duo-codes
  "Gets the DUO codes from the database."
  []
  (->> @code-by-id
       vals
       (mapv enrich-duo-code)))

(comment
  (get-duo-codes))

(defn join-duo-codes [k x]
  (let [unknown-value {:label {:en "unknown code"}
                       :description {:en "Unknown code"}}
        code-or-default (fn [duo] (get @code-by-id (:id duo) (merge unknown-value duo)))]
    (update-in x [k :duo/codes] (partial mapv code-or-default))))


(comment
  ;; Here is code to load the latest DUO release
  ;; check it and potentially update application.
  ;; This is a manual process for the developers
  ;; as a new release may bring codes that need
  ;; special handling.

  (def duos (fetch-duo-releases-latest))


  ;; check to see if the new release is the still the same
  (assert (= supported-duo-release-tag (:tag duos)))


  ;; check if any new types appeared that are not defined yet
  (def missing-types
    (difference (set (map :id (:codes duos)))
                (set (concat simple-codes (keys complex-codes)))
                abstract-codes))
  (assert (empty? missing-types))


  (spit "duo.edn" (-> duos :codes vec)))
