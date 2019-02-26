(ns rems.json
  (:require [cognitect.transit :as transit]
            [cuerdas.core :refer [numeric? parse-number]]
            [clojure.test :refer [deftest is testing]]
            [jsonista.core :as j]
            [muuntaja.core :as muuntaja])
  (:import [org.joda.time DateTime ReadableInstant]
           [com.fasterxml.jackson.datatype.joda JodaModule]))

(def joda-time-writer
  (transit/write-handler
   "m"
   (fn [v] (-> ^ReadableInstant v .getMillis))
   (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(def muuntaja
  (muuntaja/create
   (-> muuntaja/default-options
       (assoc-in [:formats "application/json" :encoder-opts :modules] [(JodaModule.)])
       (assoc-in [:formats "application/transit+json" :encoder-opts :handlers] {DateTime joda-time-writer}))))

;; Sometimes we have ints as keys in clj maps, which are stringified in JSON
(defn- str->keyword-or-number [str]
  (if (numeric? str)
    (parse-number str)
    (keyword str)))

(def mapper
  (j/object-mapper
   {:modules [(JodaModule.)]
    :decode-key-fn str->keyword-or-number}))

(defn generate-string [obj]
  (j/write-value-as-string obj mapper))

(defn parse-string [json]
  (j/read-value json mapper))

(deftest test-muuntaja
  (let [format "application/json"]
    (testing format
      (testing "encoding"
        (is (= "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0)})))))

      ;; TODO: decoding dates requires coercion
      #_(testing "decoding"
          (is (= {:date-time (.toDate (DateTime. 2000 1 1 12 0))}
                 (muuntaja/decode muuntaja format "{\"date-time\":\"2000-01-01T10:00:00.000Z\"}"))))))

  (let [format "application/transit+json"]
    (testing format
      (testing "encoding"
        (is (= "[\"^ \",\"~:date-time\",\"~m946720800000\"]"
               (slurp (muuntaja/encode muuntaja format {:date-time (DateTime. 2000 1 1 12 0)})))))

      (testing "decoding")
      (is (= {:date-time (.toDate (DateTime. 2000 1 1 12 0))}
             (muuntaja/decode muuntaja format "[\"^ \",\"~:date-time\",\"~m946720800000\"]"))))))
