(ns rems.read-gitlog
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [rems.git :as git])
  (:import [java.io IOException]))

(def ^:private version-description-file "git-describe.txt")
(def ^:private version-revision-file "git-revision.txt")

(defn- read-file [name]
  (some-> name
          io/resource
          slurp
          str/trim))

(defmacro read-current-version []
  (try
    (let [version (read-file version-description-file)
          revision (read-file version-revision-file)]
      (when (and version revision)
        {:version version
         :revision revision}))
    (catch IOException _ nil)))
