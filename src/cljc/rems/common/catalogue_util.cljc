(ns rems.common.catalogue-util
  (:require [clojure.string :as str]
            [rems.text :refer [localized]]))

(defn- urn? [resid]
  (and resid (str/starts-with? resid "urn:")))

(defn- urn-catalogue-item-url [resid {:keys [urn-organization]}]
  (when (urn? resid)
    (str (or urn-organization "http://urn.fi/") resid)))

;; EGA catalogue items (i.e. datasets) look like EGAD00001006673 and could be linked to e.g. https://ega-archive.org/datasets/EGAD00001006673

(defn- ega-dataset? [resid]
  (and resid (str/starts-with? resid "EGAD")))

(defn- ega-catalogue-item-url [resid {:keys [enable-ega ega-organization]}]
  (when (and enable-ega (ega-dataset? resid))
    (str (or ega-organization "https://ega-archive.org/datasets/") resid)))

;; Resource can have different schemas here (V2Resource vs. CatalogueItem)
(defn catalogue-item-more-info-url [resource-or-item language config]
  (let [default-language (:default-language config)]
    (or (get-in resource-or-item [:catalogue-item/infourl language])
        (get-in resource-or-item [:catalogue-item/infourl default-language])
        (get-in resource-or-item [:localizations language :infourl])
        (get-in resource-or-item [:localizations default-language :infourl])
        (urn-catalogue-item-url (:resource/ext-id resource-or-item) config)
        (urn-catalogue-item-url (:resid resource-or-item) config)
        (ega-catalogue-item-url (:resource/ext-id resource-or-item) config)
        (ega-catalogue-item-url (:resid resource-or-item) config))))

;; TODO: test here?
