(ns rems.common.catalogue-util
  (:require [clojure.string :as str]))

(defn urn? [resid]
  (and resid (str/starts-with? resid "urn:")))

(defn urn-catalogue-item-link [{:keys [resid]} {:keys [urn-organization]}]
  (when (urn? resid)
    (str (or urn-organization "http://urn.fi/") resid)))

;; EGA catalogue items (i.e. datasets) look like   https://ega-archive.org/datasets/EGAD00001006673

(defn ega-dataset? [resid]
  (and resid (str/starts-with? resid "EGAD")))

(defn ega-catalogue-item-link [{:keys [resid]} {:keys [ega-organization]}]
  (when (ega-dataset? resid)
    (str (or ega-organization "https://ega-archive.org/datasets/") resid)))
