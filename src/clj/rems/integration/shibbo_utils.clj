(ns rems.integration.shibbo-utils
  (:import (javax.servlet ServletRequest)))

(def ^:private shibbo-attribs
  ["commonName" "displayName" "eduPersonAffiliation" "eppn" "mail" "surname" "schacHomeOrganization" "schacHomeOrganizationType"])

(def not-blank? (complement clojure.string/blank?))

(defn ^:private get-ajp-attributes
  "Extracts attributes from servlet-request.
  Recommended way to pass Shibboleth env vars to JVM is by
  AJP protocol."
  [names req]
  (when-let [^ServletRequest request (:servlet-request req)]
      (into {}
            (for [n names
                  :let [val (.getAttribute request n)]
                  :when val]
              [n
               ;;Hax to fix tomcat double utf-8 encoding problem
               (new String (.getBytes val "ISO-8859-1") "UTF-8")]))))

(defn get-attributes [request env & {:keys [names] :or {names shibbo-attribs}}]
  (get-ajp-attributes names request))
