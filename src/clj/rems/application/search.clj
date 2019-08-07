(ns rems.application.search
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.db.applications :as applications]
            [rems.util :refer [conj-set]])
  (:import [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document StringField Field$Store TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode DirectoryReader]
           [org.apache.lucene.queryparser.flexible.standard StandardQueryParser]
           [org.apache.lucene.search IndexSearcher ScoreDoc TopDocs]
           [org.apache.lucene.store Directory NIOFSDirectory]))

(def ^:private analyzer (StandardAnalyzer.))

(mount/defstate ^Directory directory
  :start (let [dir (NIOFSDirectory. (.toPath (io/file "search-index")))]
           (with-open [writer (IndexWriter. dir (-> (IndexWriterConfig. analyzer)
                                                    (.setOpenMode IndexWriterConfig$OpenMode/CREATE)))]
             (.deleteAll writer))
           dir)
  :stop (.close directory))


;; TODO: store last event ID inside the index?
(mount/defstate applications
  :start (events-cache/new))

(defn- update-applications [state event]
  (let [app-id (:application/id event)]
    (if app-id
      (update state ::needs-reindexing conj-set app-id)
      state)))

(defn refresh! []
  (events-cache/refresh! applications
                         (fn [state events]
                           (reduce update-applications state events)))
  (let [app-ids (::needs-reindexing (:state @applications))]
    (when-not (empty? app-ids)
      (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                     (.setOpenMode IndexWriterConfig$OpenMode/APPEND)))]
        (doseq [app-id app-ids]
          (log/info "Indexing application" app-id)
          (let [app (applications/get-unrestricted-application app-id)
                doc (Document.)]
            (.add doc (StringField. "id" (str (:application/id app)) Field$Store/YES))
            (.add doc (TextField. "applicant" (str (:application/applicant app)) Field$Store/NO))
            (.addDocument writer doc))))
      (swap! applications (fn [obj]
                            (update-in obj [:state ::needs-reindexing] set/difference app-ids))))))


(defn- get-application-ids [^IndexSearcher searcher ^TopDocs results]
  (doall (for [^ScoreDoc hit (.-scoreDocs results)]
           (let [doc (.doc searcher (.-doc hit))
                 id (.get doc "id")]
             (Long/parseLong id)))))

(defn find-applications [^String query]
  (refresh!) ; TODO: call from a background thread asynchronously?
  (with-open [reader (DirectoryReader/open directory)]
    (let [searcher (IndexSearcher. reader)
          results (.search searcher
                           (-> (StandardQueryParser. analyzer)
                               (.parse query "applicant")) ; TODO: change defaultField to full text search
                           10000)]
      (set (get-application-ids searcher results)))))
