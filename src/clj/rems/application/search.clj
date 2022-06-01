(ns rems.application.search
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [ALL select]]
            [mount.core :as mount]
            [rems.common.application-util :as application-util]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.events :as events]
            [rems.text :as text]
            [rems.util :refer [delete-directory-contents-recursively]])
  (:import [org.apache.lucene.analysis Analyzer]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document StringField Field$Store TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode Term]
           [org.apache.lucene.queryparser.flexible.core QueryNodeException]
           [org.apache.lucene.queryparser.flexible.standard StandardQueryParser]
           [org.apache.lucene.search IndexSearcher ScoreDoc TopDocs SearcherManager SearcherFactory Query]
           [org.apache.lucene.store Directory NIOFSDirectory]))

(def ^:private ^Analyzer analyzer (StandardAnalyzer.))

(def ^:private ^String app-id-field "app-id")

;; Only one IndexWriter may use the directory at a time.
;; Otherwise it'll throw a LockObtainFailedException.
(def ^:private index-lock (Object.))

(mount/defstate ^Directory search-index
  :start (let [index-dir (.toPath (io/file (:search-index-path env)))]
           (locking index-lock
             ;; delete old index
             (delete-directory-contents-recursively (.toFile index-dir))

             (let [directory (NIOFSDirectory. index-dir)]
               ;; create a new empty index by creating and closing an IndexWriter, otehrwise SearcherManager will fail
               (.close (IndexWriter. directory (IndexWriterConfig. analyzer)))
               (atom {::directory directory
                      ::searcher-manager (SearcherManager. directory (SearcherFactory.))
                      ::last-processed-event-id 0}))))
  :stop (do
          (.close ^SearcherManager (::searcher-manager @search-index))
          (.close ^Directory (::directory @search-index))))

(defn- indexed-member-attributes [member]
  [(:userid member)
   (application-util/get-member-name member)
   (:email member)])

(defn- index-terms-for-application [app]
  {:id (->> [(:application/id app)
             (:application/assigned-external-id app)
             (:application/generated-external-id app)]
            (str/join " "))
   :applicant (->> (indexed-member-attributes (:application/applicant app))
                   (str/join " "))
   :member (->> (:application/members app)
                (mapcat indexed-member-attributes)
                (str/join " "))
   :title (:application/description app)
   :resource (->> (:application/resources app)
                  (mapcat (fn [resource]
                            (remove empty?
                                    (conj (vals (:catalogue-item/title resource))
                                          (:resource/ext-id resource)))))
                  (str/join " "))
   :state (->> (:languages env)
               (map (fn [lang]
                      (text/with-language lang
                        #(text/localize-state (:application/state app)))))
               (str/join " "))
   :todo (->> (:languages env)
              (map (fn [lang]
                     (text/with-language lang
                       #(text/localize-todo (:application/todo app)))))
              (cons (str (:application/todo app)))
              (str/join " "))
   :form (->> (select [:application/forms ALL :form/fields ALL :field/value] app) ;; TODO: filter out checkboxes, attachments etc?
              (str/join " "))})

(defn- index-application! [^IndexWriter writer app]
  (let [app-id (str (:application/id app))]
    (log/debug "Indexing application" app-id)
    (try
      (let [doc (Document.)
            terms (index-terms-for-application app)]
        ;; metadata
        (.add doc (StringField. app-id-field app-id Field$Store/YES))
        ;; searchable fields
        (doseq [[k v] terms]
          (.add doc (TextField. (name k) v Field$Store/NO)))
        (.add doc (TextField. "all" (str/join " " (vals (into (sorted-map) terms))) Field$Store/NO))
        (.updateDocument writer (Term. app-id-field app-id) doc))
      (catch Throwable t
        (throw (Error. (str "Error indexing application " app-id) t))))))

(defn refresh! []
  (locking index-lock
    (let [{::keys [directory ^SearcherManager searcher-manager last-processed-event-id]} @search-index
          events (events/get-all-events-since last-processed-event-id)]
      (when-not (empty? events)
        (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                       (.setOpenMode IndexWriterConfig$OpenMode/APPEND)))]
          (let [app-ids (distinct (map :application/id events))]
            (log/info "Start indexing" (count app-ids) "applications...")
            (doseq [app-id app-ids]
              (index-application! writer (applications/get-application app-id)))
            (log/info "Finished indexing" (count app-ids) "applications")))
        (.maybeRefresh searcher-manager)
        (swap! search-index assoc ::last-processed-event-id (:event/id (last events)))))))

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
                 app-id (.get doc app-id-field)]
             (Long/parseLong app-id)))))

(defn- ^Query parse-query [^String query]
  (try
    (-> (StandardQueryParser. analyzer)
        (.parse query "all"))
    (catch QueryNodeException e
      (log/info (str "Failed to parse query '" query "', " e))
      nil)))

(defn find-applications [^String query]
  (when-let [query (parse-query query)]
    (refresh!) ; TODO: call from a background thread asynchronously?
    (with-searcher
      (fn [^IndexSearcher searcher]
        (->> (.search searcher query Integer/MAX_VALUE)
             (get-application-ids searcher)
             set)))))
