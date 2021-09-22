(ns rems.db.duo
  (:require [clj-http.client :as http]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.set :refer [difference]]
            [clojure.string :as str]
            [medley.core :refer [remove-vals update-existing]]
            [rems.config]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :refer [LocalizedString]]
            [schema.coerce :as coerce]
            [schema.core :as s]))

;; The CSV format has English texts only
(s/defschema DuoCodeCsv
  {:id s/Str
   (s/optional-key :shorthand) (s/maybe s/Str)
   :label s/Str
   :description s/Str})

;; The DB format has localized texts
(s/defschema DuoCodeDb
  {:id s/Str
   (s/optional-key :shorthand) (s/maybe s/Str)
   :label LocalizedString
   :description LocalizedString})

(def ^:private coerce-DuoCodeCsv
  (coerce/coercer! DuoCodeCsv coerce/string-coercion-matcher))

(def ^:private coerce-DuoCodeDb
  (coerce/coercer! DuoCodeDb coerce/string-coercion-matcher))

(def ^:private validate-DuoCodeDb
  (s/validator DuoCodeDb))

(defn- rows->maps
  "Transform CSV `rows` into maps with the keys from the first header row."
  [rows]
  (mapv zipmap
        (->> (first rows) ; header
             (map keyword)
             repeat)
        (rest rows)))

(defn upsert-duo-code!
  "Insert a new or update an existing DUO code into the database."
  [duo]
  (let [data (-> duo
                 validate-DuoCodeDb
                 (dissoc :id)
                 (->> (remove-vals #(cond (string? %) (str/blank? %)
                                          (coll? %) (empty? %)
                                          :else %)))
                 json/generate-string)]
    (when (:id duo)
      (db/upsert-duo-code! {:id (:id duo)
                            :data data}))))

(defn- read-zip-entries
  "Read the zip-file entries from the `stream` and returns those matching `re`."
  [stream re]
  (when stream
    (loop [files []]
      (if-let [entry (.getNextEntry stream)]
        (if (re-matches re (.getName entry))
          (let [baos (java.io.ByteArrayOutputStream.)]
            (io/copy stream baos)
            (recur (conj files {:name (.getName entry)
                                :bytes (.toByteArray baos)})))
          (recur files))
        files))))

(defn fetch-duo-releases-latest
  "Fetches the latest DUO codes from the GitHub repository.

  Returns the unpacked formatted result."
  []
  (let [release-response (http/get "https://api.github.com/repos/EBISPOT/DUO/releases/latest"
                                   {:accept :json
                                    :as :json})
        release-body (:body release-response)
        release-tag (:tag_name release-body)
        zip-response (http/get (:zipball_url release-body) {:as :stream})
        csv-bytes (-> zip-response :body (java.util.zip.ZipInputStream.) (read-zip-entries #".*/duo.csv$") first :bytes)]
    (with-open [reader (io/reader csv-bytes)]
      (let [codes (for [duo (->> reader csv/read-csv rows->maps)]
                    (-> duo
                        coerce-DuoCodeCsv
                        (update-existing :label (fn [en] {:en en}))
                        (update-existing :description (fn [en] {:en en}))))]
        {:tag release-tag
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

(defn- format-duo-code
  "Formats the database DUO code to a map."
  [duo]
  (coerce-DuoCodeDb (merge {:id (:id duo)}
                           (json/parse-string (:data duo)))))

(defn- enrich-duo-code
  "Adds convenience attributes to DUO codes."
  [duo]
  (merge duo
         (when (contains? simple-codes (:id duo)) {:simple? true})))

(defn get-duo-codes
  "Gets the DUO codes from the database."
  []
  (->> (db/get-duo-codes)
       (mapv format-duo-code)
       (mapv enrich-duo-code)))

(comment
  (get-duo-codes))




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


  ;; this is for saving the codes into the database
  ;; and it generates code for `load-duo-codes!` function
  (for [duo (:codes duos)]
    `(~'upsert-duo-code! ~(select-keys duo [:id :shorthand :label :description]))))



(defn load-duo-codes!
  "Loads DUO codes into the database.

  NB: the code here is generated by the commented code above. The idea is to update
  these if/when there is a new release."
  []
  (upsert-duo-code!
   {:id "DUO:0000021"
    :shorthand "IRB"
    :label {:en "ethics approval required"}
    :description {:en "This data use modifier indicates that the requestor must provide documentation of local IRB/ERB approval."}})
  (upsert-duo-code!
   {:id "DUO:0000006"
    :shorthand "HMB"
    :label {:en "health or medical or biomedical research"}
    :description {:en "This data use permission indicates that use is allowed for health/medical/biomedical purposes; does not include the study of population origins or ancestry."}})
  (upsert-duo-code!
   {:id "DUO:0000019"
    :shorthand "PUB"
    :label {:en "publication required"}
    :description {:en "This data use modifier indicates that requestor agrees to make results of studies using the data available to the larger scientific community."}})
  (upsert-duo-code!
   {:id "DUO:0000026"
    :shorthand "US"
    :label {:en "user specific restriction"}
    :description {:en "This data use modifier indicates that use is limited to use by approved users."}})
  (upsert-duo-code!
   {:id "DUO:0000044"
    :shorthand "NPOA"
    :label {:en "population origins or ancestry research prohibited"}
    :description {:en "This data use modifier indicates use for purposes of population origin or ancestry research is prohibited."}})
  (upsert-duo-code!
   {:id "DUO:0000020"
    :shorthand "COL"
    :label {:en "collaboration required"}
    :description {:en "This data use modifier indicates that the requestor must agree to collaboration with the primary study investigator(s)."}})
  (upsert-duo-code!
   {:id "DUO:0000046"
    :shorthand "NCU"
    :label {:en "non-commercial use only"}
    :description {:en "This data use modifier indicates that use of the data is limited to not-for-profit use."}})
  (upsert-duo-code!
   {:id "DUO:0000018"
    :shorthand "NPUNCU"
    :label {:en "not for profit non commercial use only"}
    :description {:en "This data use modifier indicates that use of the data is limited to not-for-profit organizations and not-for-profit use non-commercial use."}})
  (upsert-duo-code!
   {:id "DUO:0000012"
    :shorthand "RS"
    :label {:en "research specific restrictions"}
    :description {:en "This data use modifier indicates that use is limited to studies of a certain research type."}})
  (upsert-duo-code!
   {:id "DUO:0000025"
    :shorthand "TS"
    :label {:en "time limit on use"}
    :description {:en "This data use modifier indicates that use is approved for a specific number of months."}})
  (upsert-duo-code!
   {:id "DUO:0000004"
    :shorthand "NRES"
    :label {:en "no restriction"}
    :description {:en "This data use permission indicates there is no restriction on use."}})
  (upsert-duo-code!
   {:id "DUO:0000045"
    :shorthand "NPU"
    :label {:en "not for profit organisation use only"}
    :description {:en "This data use modifier indicates that use of the data is limited to not-for-profit organizations."}})
  (upsert-duo-code!
   {:id "DUO:0000017"
    :shorthand ""
    :label {:en "data use modifier"}
    :description {:en "Data use modifiers indicate additional conditions for use."}})
  (upsert-duo-code!
   {:id "DUO:0000011"
    :shorthand "POA"
    :label {:en "population origins or ancestry research only"}
    :description {:en "This data use permission indicates that use of the data is limited to the study of population origins or ancestry."}})
  (upsert-duo-code!
   {:id "DUO:0000024"
    :shorthand "MOR"
    :label {:en "publication moratorium"}
    :description {:en "This data use modifier indicates that requestor agrees not to publish results of studies until a specific date."}})
  (upsert-duo-code!
   {:id "DUO:0000016"
    :shorthand "GSO"
    :label {:en "genetic studies only"}
    :description {:en "This data use modifier indicates that use is limited to genetic studies only (i.e. studies that include genotype research alone or both genotype and phenotype research but not phenotype research exclusively)"}})
  (upsert-duo-code!
   {:id "DUO:0000029"
    :shorthand "RTN"
    :label {:en "return to database or resource"}
    :description {:en "This data use modifier indicates that the requestor must return derived/enriched data to the database/resource."}})
  (upsert-duo-code!
   {:id "DUO:0000043"
    :shorthand "CC"
    :label {:en "clinical care use"}
    :description {:en "This data use modifier indicates that use is allowed for clinical use and care."}})
  (upsert-duo-code!
   {:id "DUO:0000015"
    :shorthand "NMDS"
    :label {:en "no general methods research"}
    :description {:en "This data use modifier indicates that use does not allow methods development research (e.g. development of software or algorithms)."}})
  (upsert-duo-code!
   {:id "DUO:0000028"
    :shorthand "IS"
    :label {:en "institution specific restriction"}
    :description {:en "This data use modifier indicates that use is limited to use within an approved institution."}})
  (upsert-duo-code!
   {:id "DUO:0000022"
    :shorthand "GS"
    :label {:en "geographical restriction"}
    :description {:en "This data use modifier indicates that use is limited to within a specific geographic region."}})
  (upsert-duo-code!
   {:id "DUO:0000007"
    :shorthand "DS"
    :label {:en "disease specific research"}
    :description {:en "This data use permission indicates that use is allowed provided it is related to the specified disease."}})
  (upsert-duo-code!
   {:id "DUO:0000001"
    :shorthand ""
    :label {:en "data use permission"}
    :description {:en "A data item that is used to indicate consent permissions for datasets and/or materials and relates to the purposes for which datasets and/or material might be removed stored or used."}})
  (upsert-duo-code!
   {:id "DUO:0000042"
    :shorthand "GRU"
    :label {:en "general research use"}
    :description {:en "This data use permission indicates that use is allowed for general research use for any research purpose."}})
  (upsert-duo-code!
   {:id "DUO:0000027"
    :shorthand "PS"
    :label {:en "project specific restriction"}
    :description {:en "This data use modifier indicates that use is limited to use within an approved project."}}))
