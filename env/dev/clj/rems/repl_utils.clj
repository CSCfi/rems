(ns rems.repl-utils
  (:require [clojure.pprint :refer [pprint]]
            [muuntaja.core :as muuntaja]
            [rems.api :as api])
  (:import (java.awt Toolkit)
           (java.awt.datatransfer DataFlavor)))

(defn read-clipboard []
  (-> (Toolkit/getDefaultToolkit)
      (.getSystemClipboard)
      (.getData DataFlavor/stringFlavor)))

(defn decode-transit [data]
  (muuntaja/decode api/muuntaja "application/transit+json" data))

(defn pptransit
  ([]
   (pptransit (read-clipboard)))
  ([data]
   (pprint (decode-transit data))))
