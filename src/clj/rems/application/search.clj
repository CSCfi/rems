(ns rems.application.search
  (:require [clojure.java.io :as io]
            [mount.core :as mount]
            [rems.db.applications :as applications])
  (:import [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document StringField Field$Store TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode DirectoryReader]
           [org.apache.lucene.queryparser.flexible.standard StandardQueryParser]
           [org.apache.lucene.search IndexSearcher ScoreDoc TopDocs]
           [org.apache.lucene.store Directory NIOFSDirectory]))

(mount/defstate ^Directory directory
  :start (NIOFSDirectory. (.toPath (io/file "search-index")))
  :stop (.close directory))

(def ^:private analyzer (StandardAnalyzer.))

(defn- get-application-ids [^IndexSearcher searcher ^TopDocs results]
  (doall (for [^ScoreDoc hit (.-scoreDocs results)]
           (let [doc (.doc searcher (.-doc hit))
                 id (.get doc "id")]
             (Long/parseLong id)))))

(defn find-applications [^String query]
  ;; TODO: incremental indexing
  (let [apps (applications/get-all-unrestricted-applications)]

    (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                   (.setOpenMode IndexWriterConfig$OpenMode/CREATE)))]
      (doseq [app apps]
        (let [doc (Document.)]
          (.add doc (StringField. "id" (str (:application/id app)) Field$Store/YES))
          (.add doc (TextField. "applicant" (str (:application/applicant app)) Field$Store/NO))
          (.addDocument writer doc))))

    (with-open [reader (DirectoryReader/open directory)]
      (let [searcher (IndexSearcher. reader)
            results (.search searcher
                             (-> (StandardQueryParser. analyzer)
                                 (.parse query "applicant")) ; TODO: change defaultField to full text search
                             10000)]
        (set (get-application-ids searcher results))))))
