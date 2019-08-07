(ns rems.application.search
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.db.applications :as applications]
            [rems.db.events :as events])
  (:import [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document StringField Field$Store TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode]
           [org.apache.lucene.queryparser.flexible.standard StandardQueryParser]
           [org.apache.lucene.search IndexSearcher ScoreDoc TopDocs SearcherManager SearcherFactory]
           [org.apache.lucene.store Directory NIOFSDirectory]))

(def ^:private analyzer (StandardAnalyzer.))

(mount/defstate ^Directory search-index
  :start (let [directory (NIOFSDirectory. (.toPath (io/file "search-index")))]
           (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                          (.setOpenMode IndexWriterConfig$OpenMode/CREATE)))]
             (.deleteAll writer))
           (atom {::directory directory
                  ::searcher-manager (SearcherManager. directory (SearcherFactory.))
                  ::last-processed-event-id 0}))
  :stop (do
          (.close ^SearcherManager (::searcher-manager @search-index))
          (.close ^Directory (::directory @search-index))))

(defn- index-application! [^IndexWriter writer app]
  (log/info "Indexing application" (:application/id app))
  (let [doc (Document.)]
    (.add doc (StringField. "id" (str (:application/id app)) Field$Store/YES))
    (.add doc (TextField. "applicant" (str (:application/applicant app)) Field$Store/NO))
    (.addDocument writer doc)))

(defn refresh! []
  (let [{::keys [directory ^SearcherManager searcher-manager last-processed-event-id]} @search-index
        events (events/get-all-events-since last-processed-event-id)]
    (when-not (empty? events)
      (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                     (.setOpenMode IndexWriterConfig$OpenMode/APPEND)))]
        (doseq [app-id (set (map :application/id events))]
          (index-application! writer (applications/get-unrestricted-application app-id))))
      (.maybeRefresh searcher-manager)
      (swap! search-index assoc ::last-processed-event-id (:event/id (last events))))))

(defn- with-searcher [f]
  (let [searcher-manager ^SearcherManager (::searcher-manager @search-index)
        searcher ^IndexSearcher (.acquire searcher-manager)]
    (try
      (f searcher)
      (finally
        (.release searcher-manager searcher)))))

(defn- get-application-ids [^IndexSearcher searcher ^TopDocs results]
  (doall (for [^ScoreDoc hit (.-scoreDocs results)]
           (let [doc (.doc searcher (.-doc hit))
                 id (.get doc "id")]
             (Long/parseLong id)))))

(defn find-applications [^String query]
  (refresh!) ; TODO: call from a background thread asynchronously?
  (with-searcher
   (fn [^IndexSearcher searcher]
     (let [results (.search searcher
                            (-> (StandardQueryParser. analyzer)
                                (.parse query "applicant")) ; TODO: change defaultField to full text search
                            10000)]
       (set (get-application-ids searcher results))))))
