(ns rems.common.attachment-types
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

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
