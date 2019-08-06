(ns rems.application.search
  (:require [clojure.java.io :as io]
            [mount.core :as mount]
            [rems.db.applications :as applications])
  (:import [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document StringField Field$Store TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode]
           [org.apache.lucene.store Directory MMapDirectory NIOFSDirectory]))

(mount/defstate ^Directory directory
  :start (NIOFSDirectory. (.toPath (io/file "search-index")))
  :stop (.close directory))

(def ^:private analyzer (StandardAnalyzer.))

(defn find-applications [query]
  (let [apps (applications/get-all-unrestricted-applications)]

    (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                   (.setOpenMode IndexWriterConfig$OpenMode/CREATE)))]
      (doseq [app apps]
        (let [doc (Document.)]
          (.add doc (StringField. "id" (str (:application/id app)) Field$Store/YES))
          (.add doc (TextField. "applicant" (str (:application/applicant app)) Field$Store/NO))
          (.addDocument writer doc))))

    ;; TODO: query the index
    (->> apps
         ;; TODO: remove walking skeleton
         (filter #(= query (name (:application/applicant %))))
         (map :application/id)
         (set))))
