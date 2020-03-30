(ns rems.common.attachment-types
  (:require [clojure.string :as str]))

(def allowed-extensions
  [".pdf"
   ".txt"
   ".doc" ".docx" ".odt"
   ".ppt" ".pptx" ".odp"
   ".xls" ".xlsx" ".ods" ".csv" ".tsv"
   ".jpg" ".jpeg"
   ".png"
   ".gif"
   ".webp"
   ".tif" ".tiff"
   ".heif" ".heic"
   ".svg"])

(defn allowed-extension? [filename]
  (some? (some #(.endsWith filename %) allowed-extensions)))

(def allowed-extensions-string
  (str/join ", " allowed-extensions))
