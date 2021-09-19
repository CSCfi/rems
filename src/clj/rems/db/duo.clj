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

  Returns the unpacked, formatted result."
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

(def simple-codes
  "Codes that can be added to a resource without any additional questions."
  #{:DUO:0000004 :DUO:0000006 :DUO:0000011 :DUO:0000015 :DUO:0000016 :DUO:0000018 :DUO:0000019 :DUO:0000021 :DUO:0000029 :DUO:0000042 :DUO:0000043 :DUO:0000044 :DUO:0000045 :DUO:0000046})

(def complex-codes
  "Codes that require specific handling or an additional question."
  {:DUO:0000007 {:type :MONDO}
   :DUO:0000012 {:type :topic}
   :DUO:0000020 {:type :collaboration}
   :DUO:0000022 {:type :location}
   :DUO:0000024 {:type :date}
   :DUO:0000025 {:type :months}
   :DUO:0000026 {:type :users}
   :DUO:0000027 {:type :project}
   :DUO:0000028 {:type :institute}})

(comment
  (def duos (fetch-duo-releases-latest))
  (def tag (:tag duos))
  ;; this is for generating the translations
  (def translations
    (apply sorted-map (mapcat (fn [x] [(keyword (:id x))
                                       {:label (get-in x [:label :en])
                                        :description (get-in x [:description :en])}]) (:codes duos))))
  ;; this is for saving the codes into the database
  (doseq [duo (:codes duos)]
    (upsert-duo-code! (select-keys duo [:id :shorthand :label :description]))))

(comment
  (def abstract-types #{:DUO:0000001 :DUO:0000017}) ; not concrete types
  (def missing-types ; new types not defined yet
    (difference (set (map keyword (map :id (:codes duos))))
                (set (concat simple-codes (keys complex-codes)))
                abstract-types)))

(defn- format-duo-code
  "Formats the database DUO code to a map."
  [duo]
  (coerce-DuoCodeDb (merge {:id (:id duo)}
                           (json/parse-string (:data duo)))))

(defn get-duo-codes
  "Gets the DUO codes from the database."
  []
  (->> (db/get-duo-codes)
       (mapv format-duo-code)))

(comment
  (get-duo-codes))
