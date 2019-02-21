(ns rems.json
  (:require [cheshire.core :as cheshire-core]
            [cheshire.generate :as cheshire-generate]
            [cognitect.transit :as transit]
            [cuerdas.core :refer [numeric? parse-number]]
            [muuntaja.core :as muuntaja]
            [muuntaja.format.json :refer [json-format]]
            [muuntaja.format.transit :as transit-format]
            [jsonista.core :as j])
  (:import [org.joda.time DateTime ReadableInstant]
           [com.fasterxml.jackson.datatype.joda JodaModule]))

(def joda-time-writer
  (transit/write-handler
   "m"
   (fn [v] (-> ^ReadableInstant v .getMillis))
   (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(def muuntaja
  (muuntaja/create
   (update
    muuntaja/default-options
    :formats
    merge
    {"application/json"
     json-format

     "application/transit+json"
     {:decoder [(partial transit-format/make-transit-decoder :json)]
      :encoder [#(transit-format/make-transit-encoder
                  :json
                  (merge
                   %
                   {:handlers {DateTime joda-time-writer}}))]}})))



;; Sometimes we have ints as keys in clj maps, which are stringified in JSON
(defn- str->keyword-or-number [str]
  (if (numeric? str)
    (parse-number str)
    (keyword str)))

(def mapper
  (j/object-mapper
    {:modules [(JodaModule.)]
     :decode-key-fn str->keyword-or-number}))

(defn generate-string [foo]
  (j/write-value-as-string foo mapper))

(defn parse-string [string & _]
  (j/read-value string mapper))
