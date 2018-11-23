(ns rems.read-gitlog
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro read-current-version []
  (try
    (let [full (-> "git-describe.txt"
                   io/resource
                   slurp
                   str/trim)
          [tag commits-since commit dirty] (str/split full #"-")]
      {:full full
       :tag tag
       :commits-since commits-since
       :commit commit
       :dirty dirty})
    (catch java.io.IOException _ "")))