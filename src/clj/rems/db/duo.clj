(ns rems.db.duo
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [remove-vals]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.users :as users]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [clj-time.core :as time-core]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time DateTime]
           rems.DataException))

(s/defschema DuoCode
  {:id s/Str
   (s/optional-key :shorthand) (s/maybe s/Str)
   :label s/Str
   :description s/Str})

(def ^:private coerce-duo-code
  (coerce/coercer! DuoCode coerce/string-coercion-matcher))

(def ^:private validate-duo-code
  (s/validator DuoCode))

(defn- rows->maps [rows]
  (mapv zipmap
        (->> (first rows) ; header
             (map keyword)
             repeat)
        (rest rows)))

(defn upsert-duo-code! [duo]
  (let [data (-> duo
                 (dissoc :id)
                 (->> (remove-vals str/blank?))
                 json/generate-string)]
    (when (:id duo)
      (db/upsert-duo-code! {:id (:id duo)
                            :data data}))))

(defn load-duo-codes! []
  (let [code-file (io/file "duo.csv")]
    (log/info "Loading DUO codes from" (.getAbsolutePath code-file))
    (when (.exists code-file)
      (with-open [reader (io/reader code-file)]
        (let [duos (->> reader
                        csv/read-csv
                        rows->maps)]
          (doseq [duo duos]
            (upsert-duo-code! duo))
          (log/info "Loaded" (count duos) "codes"))))))

(defn- format-duo-code [duo]
  (coerce-duo-code (merge {:id (:id duo)}
                          (json/parse-string (:data duo)))))

(defn get-duo-codes []
  (->> (db/get-duo-codes)
       (mapv format-duo-code)))

(comment
  (load-duo-codes!)
  (get-duo-codes))
