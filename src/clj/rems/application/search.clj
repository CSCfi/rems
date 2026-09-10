(ns rems.application.search
  (:require [clj-time.core :as time-core]
            [clj-time.format]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [ALL select]]
            [mount.core :as mount]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [not-blank]]
            [rems.config :refer [env]]
            [rems.db.applications]
            [rems.db.events]
            [rems.text :as text]
            [rems.util :refer [delete-directory-contents-recursively]])
  (:import [org.apache.lucene.analysis Analyzer]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.document Document Field$Store StringField TextField DateTools DateTools$Resolution]
           [org.apache.lucene.index IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode Term]
           [org.apache.lucene.queryparser.flexible.core QueryNodeException]
           [org.apache.lucene.queryparser.flexible.standard StandardQueryParser]
           [org.apache.lucene.search IndexSearcher ScoreDoc SearcherFactory SearcherManager TopDocs Query]
           [org.apache.lucene.store Directory NIOFSDirectory]
           [org.joda.time DateTime]))

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


(defn ->lucene-date-str
  "Transform Joda DateTime (clj-time) `dt` to Lucene date string.
  ```clj
  (->lucene-date-str (DateTime. \"2026-07-28T06:14:43.717Z\"))
  ;=> 20260728
  ```"
  [^DateTime dt]
  (when dt
    (DateTools/dateToString (. dt toDate)
                            DateTools$Resolution/DAY)))

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
                        (text/localize-state (:application/state app)))))
               (str/join " "))
   :year (-> app :application/last-activity time-core/year str)
   :todo (->> (:languages env)
              (map (fn [lang]
                     (text/with-language lang
                       (text/localize-todo (:application/todo app)))))
              (cons (str (:application/todo app)))
              (str/join " "))
   :form (->> (select [:application/forms ALL :form/fields ALL :field/value] app) ;; TODO: filter out checkboxes, attachments etc?
              (str/join " "))
   :first-submitted (-> app
                        :application/first-submitted
                        ->lucene-date-str)
   :last-activity (-> app
                      :application/last-activity
                      ->lucene-date-str)
   :last-applying-user-activity (-> app
                                    application-util/get-last-applying-user-event
                                    :event/time
                                    ->lucene-date-str)})

(defn- index-application! [^IndexWriter writer app]
  (let [app-id (str (:application/id app))]
    (log/debug "Indexing application" app-id)
    (try
      (let [doc (Document.)
            terms (index-terms-for-application app)]
        ;; metadata
        (.add doc (StringField. app-id-field app-id Field$Store/YES))
        ;; searchable fields
        (doseq [[k v] terms
                :when v]
          (.add doc (TextField. (name k) ^String v Field$Store/NO)))

        (.add doc (TextField. "all"
                              (->> terms
                                   (into (sorted-map))
                                   vals
                                   (str/join " "))
                              Field$Store/NO))
        (.updateDocument writer (Term. app-id-field app-id) doc))
      (catch Throwable t
        (throw (Error. (str "Error indexing application " app-id) t))))))

(defn refresh! []
  (locking index-lock
    (let [{::keys [directory ^SearcherManager searcher-manager last-processed-event-id]} @search-index
          events (rems.db.events/get-all-events-since last-processed-event-id)]
      (when-not (empty? events)
        (with-open [writer (IndexWriter. directory (-> (IndexWriterConfig. analyzer)
                                                       (.setOpenMode IndexWriterConfig$OpenMode/APPEND)))]
          (let [app-ids (distinct (map :application/id events))]
            (log/info "Start indexing" (count app-ids) "applications...")
            (doseq [app-id app-ids]
              (index-application! writer (rems.db.applications/get-application app-id)))
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

(defn- parse-query ^Query [^String query]
  (try
    (let [parser (StandardQueryParser. analyzer)]
      (.parse parser query "all"))
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

(defn filter-with-search [query]
  (let [app-ids (some-> query not-blank find-applications)]
    (cond
      (not app-ids) (filter (constantly true))
      (empty? app-ids) (filter (constantly false))
      :else (filter #(contains? app-ids %)))))


(comment
  (find-applications "applicant:alice")
  ;;=> #{7 20 27 1 24 4 15 13 6 28 25 17 3 12 2 19 11 9 5 14 26 16 10 18 8}

  (defn make-query
    "get sensible values for time range queries"
    [field-name f]
    (let [[range-start range-end]
          (->> (rems.db.applications/get-all-unrestricted-applications)
               (keep f)
               sort
               ((juxt first last))
               (map ->lucene-date-str))]
      (str field-name ":[" range-start " TO " range-end "]")))

  (make-query "last-applying-user-activity"
              (comp :event/time
                    application-util/get-last-applying-user-event))
  ;;=> "last-applying-user-activity:[20260421 TO 20260730]"

  (parse-query
   (make-query "last-applying-user-activity"
               (comp :event/time
                     application-util/get-last-applying-user-event)))
  ;;=> #object[org.apache.lucene.search.TermRangeQuery 0x6df99121 "last-applying-user-activity:[20260421 TO 20260730]"]

  (find-applications
   (make-query "last-applying-user-activity"
               (comp :event/time
                     application-util/get-last-applying-user-event)))
  ;;=> #{7 20 27 1 24 4 15 21 13 22 6 28 25 17 3 12 2 23 19 11 9 5 14 26 16 10 18 8}

  (find-applications
   (make-query "first-submitted"
               :application/first-submitted))
  ;;=> #{27 24 15 13 22 25 17 12 23 19 14 26 16 18}
  )
