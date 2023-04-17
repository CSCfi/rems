(ns rems.common.attachment-util
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [rems.common.util :refer [getx]]))

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
  (some? (some #(str/ends-with? (str/lower-case filename) %) allowed-extensions)))

(deftest test-allowed-extension?
  (is (allowed-extension? "a-simple-filename.docx"))
  (is (allowed-extension? "must_ignore_capitals.PdF"))
  (is (not (allowed-extension? "something.obviously.wrong"))))

(def allowed-extensions-string
  (str/join ", " allowed-extensions))

(defn getx-filename
  "Returns attachment filename suitable for download, for example."
  [attachment]
  (let [filename (getx attachment :attachment/filename)]
    (if (= :filename/redacted filename)
      (str "redacted_" (:attachment/id attachment) ".txt")
      filename)))

