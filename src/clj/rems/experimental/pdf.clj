(ns rems.experimental.pdf
  "Generating pdf files from applications using puppeteer and headless chrome.
   DEPRECATED, will disappear."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [rems.config :refer [env]]
            [rems.util :as util])
  (:import java.io.File))

(def ^:private +print-to-pdf-resource+ (io/resource "print-to-pdf.js"))
(def ^:private +print-to-pdf-script+ (slurp +print-to-pdf-resource+))

(defn- print-to-pdf! [user api-key url output-file]
  (let [result (shell/sh "node" "-" user api-key url output-file
                         :in +print-to-pdf-script+)]
    (log/info "OUTPUT:" (:out result))
    (log/info "ERRORS:" (:err result))
    (assert (= 0 (:exit result)))))

(defn- with-temporary-file [base ext f]
  (let [temp (File/createTempFile base ext)]
    (.deleteOnExit temp)
    (try
      (f temp)
      (finally
        (.delete temp)))))

(defn- application-url [application-id]
  (str (:public-url env) "application/" application-id))

(defn application-to-pdf [user api-key application-id]
  (with-temporary-file (str "application-" application-id "-") "pdf"
    (fn [file]
      (print-to-pdf! user api-key (application-url application-id) (.getAbsolutePath file))
      (util/file-to-bytes file))))
