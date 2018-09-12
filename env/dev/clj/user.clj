(ns user
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :as repl]
            [eftest.runner :as ef]
            [muuntaja.core :as muuntaja]
            [rems.api :as api])
  (:import (java.awt Toolkit)
           (java.awt.datatransfer DataFlavor)))

(defn reload []
  (repl/refresh)
  ; XXX: workaround to the user namespace missing after refresh
  (require 'user))

(defn run-tests [& namespaces]
  (reload)
  (ef/run-tests (ef/find-tests namespaces) {:multithread? false}))

(defn run-all-tests []
  (reload)
  (ef/run-tests (ef/find-tests "test/clj") {:multithread? false}))

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
