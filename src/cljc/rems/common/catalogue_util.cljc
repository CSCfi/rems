(ns rems.common.catalogue-util
  (:require [clojure.string :as str]))

(defn urn? [resid]
  (and resid (str/starts-with? resid "urn:")))

(defn urn-catalogue-item-link [{:keys [resid]} {:keys [urn-organization]}]
  (when (urn? resid)
    (str (or urn-organization "http://urn.fi/") resid)))
