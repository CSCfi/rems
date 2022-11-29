(ns rems.ext.duo
  "Loading and processing the Data Use Ontology

  See https://github.com/EBISPOT/DUO"
  (:require [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.set :refer [difference intersection]]
            [clojure.test :refer [deftest is testing]]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [medley.core :refer [find-first update-existing]]
            [rems.common.util :refer [index-by]]
            [rems.config]
            [rems.ext.mondo :as mondo]
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
  #{"DUO:0000004" "DUO:0000011" "DUO:0000015" "DUO:0000016" "DUO:0000018" "DUO:0000019" "DUO:0000021" "DUO:0000029" "DUO:0000043" "DUO:0000044" "DUO:0000045" "DUO:0000046"})

(def complex-codes
  "Codes that require specific handling or an additional question."
  {"DUO:0000007" {:restrictions [{:type :mondo}]}
   "DUO:0000012" {:restrictions [{:type :topic}]}
   "DUO:0000020" {:restrictions [{:type :collaboration}]}
   "DUO:0000022" {:restrictions [{:type :location}]}
   "DUO:0000024" {:restrictions [{:type :date}]}
   "DUO:0000025" {:restrictions [{:type :months}]}
   "DUO:0000026" {:restrictions [{:type :users}]}
   "DUO:0000027" {:restrictions [{:type :project}]}
   "DUO:0000028" {:restrictions [{:type :institute}]}})

;; http://purl.obolibrary.org/obo/DUO_0000001 data use permission
;; http://purl.obolibrary.org/obo/DUO_0000006 health or medical or biomedical research
;; http://purl.obolibrary.org/obo/DUO_0000017 data use modifier
;; http://purl.obolibrary.org/obo/DUO_0000042 general research use
(def abstract-codes #{"DUO:0000001" "DUO:0000006" "DUO:0000017" "DUO:0000042"}) ; not concrete types can't be used as tag

(defn load-codes []
  (when (:enable-duo rems.config/env)
    (->> (slurp (io/resource "duo.edn"))
         edn/read-string
         (index-by [:id]))))

(def ^:private code-by-id (atom nil))

(defn- get-codes
  "Return codes or a code by `id` with fallback to a default value for unknown codes.

  Loads the codes to the cache or empties it depending on if `:enable-duo` is set."
  [& [id]]
  (let [unknown-value {:label {:en "unknown code"}
                       :description {:en "Unknown code"}}]
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

(defn get-duo-codes
  "Gets the usable DUO codes from the database."
  []
  (->> (get-codes)
       (remove (comp abstract-codes :id))))

(comment
  (with-redefs [rems.config/env {:enable-duo true}]
    (get-duo-codes)))

(defn- enrich-duo-code [duo]
  (-> (get-codes (:id duo))
      (merge duo)
      mondo/join-mondo-code))

(defn enrich-duo-codes [duos]
  (mapv enrich-duo-code duos))

(defn join-duo-codes [ks x]
  (update-in x ks enrich-duo-codes))

(deftest test-join-duo-codes
  (with-redefs [rems.config/env {:enable-duo false}]
    (is (= {:id 1234
            :resource/duo {:duo/codes [{:id "DUO:0000007"
                                        :label {:en "unknown code"}
                                        :description {:en "Unknown code"}
                                        :restrictions [{:type :mondo :values [{:id "MONDO:0000004"
                                                                               :label "unknown code"}]}]}
                                       {:id "DUO:0000021"
                                        :label {:en "unknown code"}
                                        :description {:en "Unknown code"}}
                                       {:id "DUO:0000026"
                                        :label {:en "unknown code"}
                                        :description {:en "Unknown code"}}
                                       {:id "DUO:0000027"
                                        :label {:en "unknown code"}
                                        :description {:en "Unknown code"}
                                        :restrictions [{:type :project :values ["CSC/REMS"]}]}]}}
           (join-duo-codes [:resource/duo :duo/codes]
                           {:id 1234
                            :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo
                                                                                          :values [{:id "MONDO:0000004"}]}]}
                                                       {:id "DUO:0000021"}
                                                       {:id "DUO:0000026"}
                                                       {:id "DUO:0000027"
                                                        :restrictions [{:type :project
                                                                        :values ["CSC/REMS"]}]}]}}))
        "feature disabled"))
  (with-redefs [rems.config/env {:enable-duo true}]
    (is (= {:id 1234
            :resource/duo {:duo/codes [{:id "DUO:0000007"
                                        :shorthand "DS"
                                        :label {:en "disease specific research"}
                                        :description {:en "This data use permission indicates that use is allowed provided it is related to the specified disease."}
                                        :restrictions [{:type :mondo :values [{:id "MONDO:0000004" :label "adrenocortical insufficiency"}]}]}
                                       {:id "DUO:0000021"
                                        :shorthand "IRB"
                                        :label {:en "ethics approval required"}
                                        :description {:en "This data use modifier indicates that the requestor must provide documentation of local IRB/ERB approval."}}
                                       {:id "DUO:0000026"
                                        :shorthand "US"
                                        :label {:en "user specific restriction"}
                                        :description {:en "This data use modifier indicates that use is limited to use by approved users."}
                                        :restrictions [{:type :users}]}
                                       {:id "DUO:0000027"
                                        :shorthand "PS"
                                        :label {:en "project specific restriction"}
                                        :description {:en "This data use modifier indicates that use is limited to use within an approved project."}
                                        :restrictions [{:type :project :values ["CSC/REMS"]}]}]}}
           (join-duo-codes [:resource/duo :duo/codes]
                           {:id 1234
                            :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo
                                                                                          :values [{:id "MONDO:0000004"}]}]}
                                                       {:id "DUO:0000021"}
                                                       {:id "DUO:0000026"}
                                                       {:id "DUO:0000027"
                                                        :restrictions [{:type :project
                                                                        :values ["CSC/REMS"]}]}]}})))))

(defn get-restrictions [duo kw]
  (->> duo
       :restrictions
       (find-first #(= kw (:type %)))
       :values))

(defn- map-restrictions [duo kw]
  (let [values (get-restrictions duo kw)]
    (case kw
      :mondo (map :id values)
      :date (some-> values first :value time-format/parse))))

(defn check-duo-code
  "Validate that `query-code` is compatible with `dataset-code`.
   
   Matching is done first by comparing `:id` and then, if applicable, by checking
   DUO code restriction values."
  [dataset-code query-code]
  (if-not (= (:id dataset-code) (:id query-code))
    :duo/not-found
    (case (:id dataset-code)
       ;; "This data use permission indicates that use is allowed provided it
       ;;  is related to the specified disease."
      "DUO:0000007" (let [required-codes (set (map-restrictions dataset-code :mondo))
                          provided-codes (set (map-restrictions query-code :mondo))]
                      (if (seq (intersection required-codes provided-codes))
                        :duo/compatible ; one matching Mondo is enough for DUO compatibility
                        (let [unmatched-codes (difference required-codes provided-codes)
                              parent-codes (set (mapcat mondo/get-mondo-parents provided-codes))]
                          (if (seq (intersection unmatched-codes parent-codes))
                            :duo/compatible :duo/not-compatible))))
       ;; "This data use modifier indicates that requestor agrees not to publish
       ;;  results of studies until a specific date."
      "DUO:0000024" (let [required-dt (map-restrictions dataset-code :date)
                          dt (map-restrictions query-code :date)]
                      (if (nil? dt)
                        :duo/not-compatible ; when autosaving, nil is initially expected
                        (if (or (time/equal? dt required-dt)
                                (not (time/before? dt required-dt)))
                          :duo/compatible :duo/not-compatible)))

      (if (seq (:restrictions dataset-code))
        :duo/needs-manual-validation
        :duo/compatible))))

(deftest test-check-duo-code
  (with-redefs [rems.config/env {:enable-duo true}]
    (is (= :duo/compatible (check-duo-code {:id "id"} {:id "id"})))
    (is (= :duo/not-found (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "123"}]}]}
                                          {:id "DUO:0000024" :restrictions [{:type :date :values [{:value "2021-10-29"}]}]}))
        "DUO codes do not match")
    (is (= :duo/needs-manual-validation (check-duo-code {:id "DUO:0000027" :restrictions [{:type :project :values [{:value "CSC/REMS"}]}]}
                                                        {:id "DUO:0000027" :restrictions [{:type :project :values [{:value "csc rems"}]}]}))
        "Free text restriction must be manually validated")
    (testing "DUO:0000007 :mondo"
      ;; MONDO:0045024 - cancer or benign tumor
      ;; - MONDO:0005105 - melanoma
      ;;   - MONDO:0006486 - uveal melanoma
      ;; MONDO:0005015 - diabetes mellitus
      (let [query-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0005105"}
                                                                                {:id "MONDO:0100253"}]}]}]
        (is (= :duo/not-compatible (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values []}]} query-code))
            "dataset labeled with DS must have defined at least one mondo code restriction")
        (is (= :duo/not-compatible (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0006486"}]}]} query-code))
            "dataset labeled with MONDO:0006486 - 'uveal melanoma' requires a more specific code than MONDO:0005105 - 'melanoma' so it is not compatible")
        (is (= :duo/compatible (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0005105"}
                                                                                                        {:id "MONDO:0006486"}]}]} query-code))
            "dataset labeled with multiple mondo codes is compatible if query code contains at least one of those")
        (is (= :duo/compatible (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0045024"}]}]} query-code))
            "dataset labeled with MONDO:0045024 - 'cancer or benign tumor' is compatible with MONDO:0005105 - 'melanoma' because it is ancestor")
        (is (= :duo/compatible (check-duo-code {:id "DUO:0000007" :restrictions [{:type :mondo :values [{:id "MONDO:0045024"}
                                                                                                        {:id "MONDO:0006486"}]}]} query-code))
            "dataset labeled with multiple mondo codes is compatible if query code contains at least one of those because it is ancestor")))
    (testing "DUO:0000024 :date"
      (let [dataset-duo {:id "DUO:0000024" :restrictions [{:type :date :values [{:value "2022-02-08"}]}]}]
        (is (= :duo/not-compatible (check-duo-code dataset-duo {:id "DUO:0000024" :restrictions [{:type :date :values [{:value "2022-02-07"}]}]}))
            "Provided date is before required date")
        (is (= :duo/compatible (check-duo-code dataset-duo {:id "DUO:0000024" :restrictions [{:type :date :values [{:value "2022-02-08"}]}]})))
        (is (= :duo/compatible (check-duo-code dataset-duo {:id "DUO:0000024" :restrictions [{:type :date :values [{:value "2023-02-08"}]}]})))))))

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


  (spit "resources/duo.edn"
        (let [codes (->> duos
                         :codes
                         (sort-by :id)
                         (mapv enrich-duo-code))]
          (with-out-str
            (clojure.pprint/write codes :dispatch clojure.pprint/code-dispatch)))))
