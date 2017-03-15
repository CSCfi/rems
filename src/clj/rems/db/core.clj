(ns rems.db.core
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [clojure.java.jdbc :as jdbc]
   [conman.core :as conman]
   [rems.env :refer [+defaults+ *db*]]
   [rems.config :refer [env]]
   [mount.core :as mount])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            Date
            Timestamp
            PreparedStatement]))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))

(defn to-date [^java.sql.Date sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(defn create-test-data! []
  (let [meta (create-form-meta! {:title "metatitle" :user 0})
        form-en (create-form! {:title "entitle" :user 0})
        form-fi (create-form! {:title "fititle" :user 0})
        item-c (create-form-item!
                {:title "C" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-a (create-form-item!
                {:title "A" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-b (create-form-item!
                {:title "B" :type "text" :inputprompt "prompt" :user 0 :value 0})]
    (link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user 0})
    (link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user 0})
    (link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user 0})

    (create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
    (create-catalogue-item! {:title "ELFA Corpus"
                             :form (:id meta)
                             :resid 1})
    (create-catalogue-item! {:title "B"
                             :form nil
                             :resid nil})))

(defn index-by
  "Index the collection coll with given keys ks.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn load-catalogue-item-localizations!
  "Load catalogue item localizations from the database."
  []
  (->> (get-catalogue-item-localizations)
       (map #(update-in % [:langcode] keyword))
       (index-by [:catid :langcode])))

(mount/defstate catalogue-item-localizations
  :start (load-catalogue-item-localizations!))

(defn localize-catalogue-item
  "Associates localisations into a catalogue item from
  the preloaded state."
  [item]
  (assoc item :localizations (catalogue-item-localizations (:id item))))

(defn get-localized-catalogue-items []
  (map localize-catalogue-item (get-catalogue-items)))

(defn get-localized-catalogue-item [id]
  (localize-catalogue-item (get-catalogue-item id)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt ^long idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
      (doto (PGobject.)
            (.setType "jsonb")
            (.setValue (generate-string value))))

(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))
