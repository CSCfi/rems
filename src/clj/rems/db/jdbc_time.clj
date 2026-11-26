(ns rems.db.jdbc-time
  "next.jdbc protocol extensions preserving clj-time.jdbc conversions."
  (:require [clj-time.coerce :as time-coerce]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as result-set])
  (:import [java.sql Date PreparedStatement Time Timestamp]
           [org.joda.time DateTime]))

(extend-protocol result-set/ReadableColumn
  Timestamp
  (read-column-by-label [value _]
    (time-coerce/from-sql-time value))
  (read-column-by-index [value _ _]
    (time-coerce/from-sql-time value))

  Date
  (read-column-by-label [value _]
    (time-coerce/from-sql-date value))
  (read-column-by-index [value _ _]
    (time-coerce/from-sql-date value))

  Time
  (read-column-by-label [value _]
    (DateTime. value))
  (read-column-by-index [value _ _]
    (DateTime. value)))

(extend-protocol prepare/SettableParameter
  DateTime
  (set-parameter [value ^PreparedStatement statement ^long index]
    (.setTimestamp statement index (time-coerce/to-sql-time value))))
