(ns rems.json
  (:require [cheshire.core :as cheshire-core]
            [cheshire.generate :as cheshire-generate]
            [cognitect.transit :as transit]
            [muuntaja.core :as muuntaja]
            [muuntaja.format.json :refer [json-format]]
            [muuntaja.format.transit :as transit-format])
  (:import [org.joda.time DateTime ReadableInstant]))

(def joda-time-writer
  (transit/write-handler
   "m"
   (fn [v] (-> ^ReadableInstant v .getMillis))
   (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(cheshire-generate/add-encoder
 DateTime
 (fn [c jsonGenerator]
   (.writeString jsonGenerator (-> ^ReadableInstant c .getMillis .toString))))

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


(def generate-string cheshire-core/generate-string)
(def parse-string cheshire-core/parse-string)
