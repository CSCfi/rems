(ns rems.ext.duo
  "Loading and processing the Data Use Ontology

  See https://github.com/EBISPOT/DUO"
  (:require [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.set :refer [difference]]
            [clojure.test :refer [deftest is]]
            [medley.core :refer [update-existing]]
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
  #{"DUO:0000004" "DUO:0000006" "DUO:0000011" "DUO:0000015" "DUO:0000016" "DUO:0000018" "DUO:0000019" "DUO:0000021" "DUO:0000029" "DUO:0000042" "DUO:0000043" "DUO:0000044" "DUO:0000045" "DUO:0000046"})

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

(def abstract-codes #{"DUO:0000001" "DUO:0000017"}) ; not concrete types can't be used as tag

(defn- enrich-duo-code
  "Adds convenience attributes to DUO codes."
  [duo]
  (merge duo
         (get complex-codes (:id duo))))

(defn load-codes []
  (when (:enable-duo rems.config/env)
    (->> (slurp "duo.edn")
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

(defn enrich-duo-codes [duos]
  (mapv (fn [duo]
          (-> (get-codes (:id duo))
              (merge duo)
              mondo/join-mondo-code))
        duos))

(defn join-duo-codes [ks x]
  (update-in x ks enrich-duo-codes))

(deftest test-join-duo-codes
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
      "feature disabled")
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


  (spit "duo.edn" (let [codes (->> duos
                                   :codes
                                   (sort-by :id)
                                   (mapv enrich-duo-code))]
                    (with-out-str
                      (clojure.pprint/write codes :dispatch clojure.pprint/code-dispatch)))))
