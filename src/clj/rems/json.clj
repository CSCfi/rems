(ns rems.json
  (:require [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit]
            [jsonista.core :as j]
            [muuntaja.core :as muuntaja]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [com.fasterxml.jackson.datatype.joda JodaModule]
           [org.joda.time DateTime ReadableInstant DateTimeZone]
           [org.joda.time.format ISODateTimeFormat]))

(def joda-time-writer
  (transit/write-handler
   "t"
   (fn [v] (-> (ISODateTimeFormat/dateTime) (.print ^ReadableInstant v)))))

(def joda-time-reader
  (transit/read-handler time-format/parse))

(def joda-unix-time-reader
  (transit/read-handler #(DateTime. (Long/parseLong %) time/utc)))

(def muuntaja
  (muuntaja/create
   (-> muuntaja/default-options
       (assoc-in [:formats "application/json" :encoder-opts :modules] [(JodaModule.)])
       (assoc-in [:formats "application/transit+json" :encoder-opts :handlers] {DateTime joda-time-writer})
       (assoc-in [:formats "application/transit+json" :decoder-opts :handlers] {"t" joda-time-reader
                                                                                "m" joda-unix-time-reader}))))

(def mapper
  (j/object-mapper
   {:modules [(JodaModule.)]
    :decode-key-fn keyword}))

(def mapper-pretty
  (j/object-mapper
   {:modules [(JodaModule.)]
    :pretty true
    :decode-key-fn keyword}))

(defn generate-string [obj]
  (j/write-value-as-string obj mapper))

(defn generate-string-pretty [obj]
  (j/write-value-as-string obj mapper-pretty))

(defn parse-string [json]
  (j/read-value json mapper))

(deftest test-muuntaja
  (let [format "application/json"]
    (testing format
      (testing "encoding"
        (is (= "{\"date-time\":\"2000-01-01T12:00:00.000Z\"}"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}))))
        (is (= "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 (DateTimeZone/forID "Europe/Helsinki"))})))))

      (testing "decoding"
        ;; decoding dates from JSON requires coercion, so it's passed through as just plain string
        (is (= {:date-time "2000-01-01T10:00:00.000Z"}
               (muuntaja/decode muuntaja format "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"))))))

  (let [format "application/transit+json"]
    (testing format
      (testing "encoding"
        (is (= "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000Z\"]"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}))))
        (is (= "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000+02:00\"]"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0 (DateTimeZone/forID "Europe/Helsinki"))})))))

      (testing "decoding"
        (is (= {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}
               (muuntaja/decode muuntaja format "[\"^ \",\"~:date-time\",\"~t2000-01-01T12:00:00.000Z\"]")))
        (is (= {:date-time (DateTime. 2000 1 1 12 0 DateTimeZone/UTC)}
               (muuntaja/decode muuntaja format "[\"^ \",\"~:date-time\",\"~m946728000000\"]")))))))

;;; Utils for schema-based coercion

(defn- datestring->datetime [s]
  (if (string? s)
    (time-format/parse s)
    s))

(def datestring-coercion-matcher
  {DateTime datestring->datetime})

(defn coercion-matcher [schema]
  (or (datestring-coercion-matcher schema)
      (coerce/string-coercion-matcher schema)))

(deftest test-coercion-matcher
  (let [coercer (coerce/coercer! {:time DateTime :type s/Keyword} coercion-matcher)]
    (is (= {:type :foo
            :time (time/date-time 2019 03 04 10)}
           (coercer {:type "foo"
                     :time "2019-03-04T10:00:00.000Z"})))))
