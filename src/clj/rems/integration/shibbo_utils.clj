(ns rems.integration.shibbo-utils
  (:import (javax.servlet ServletRequest)))

(def ^:private shibbo-attribs
  ["eppn"])

(def not-blank? (complement clojure.string/blank?))

(defn ^:private get-ajp-attributes
  "Extracts attributes from servlet-request.
  Recommended way to pass Shibboleth env vars to JVM is by
  AJP protocol."
  [names req]
  (let [^ServletRequest request (:servlet-request req)]
    (if (some? request)
        (into {}
              (filter
                #(not-blank? (last %))
                (map #(let [val (.getAttribute request %)]
[% (if (some? val) (cast String val) nil)]) names))) nil)))

(defn get-attributes [request env & {:keys [names] :or {names shibbo-attribs}}]
  (get-ajp-attributes names request))

