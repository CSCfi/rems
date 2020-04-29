(ns rems.db.csv
  "Utilities for exporting database contents as CSV"
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [com.rpl.specter :refer [ALL select]]
            [rems.config :refer [env]]
            [rems.db.user-settings :as user-settings]
            [rems.text :as text]))

(defn print-to-csv [& {:keys [column-names rows
                              quote-strings? separator]
                       :or {separator (:csv-separator env)}}]
  (let [escape-quotes #(str/replace % "\"" "\\\"")
        maybe-quote #(if (and (string? %)
                              quote-strings?)
                       (str "\"" (escape-quotes %) "\"")
                       %)]
    (with-out-str
      (println (str/join separator (mapv maybe-quote column-names)))
      (doseq [row rows]
        (println (str/join separator (mapv maybe-quote row)))))))

;; Export applications

(def ^:private application-column-id
  {:name :t.applications.export/id
   :to-value #(->> %
                   :application/id)})

(def ^:private application-column-external-id
  {:name :t.applications.export/external-id
   :to-value #(->> %
                   :application/external-id)})

(def ^:private application-column-applicant
  {:name :t.applications.export/applicant
   :to-value #(->> %
                   :application/applicant
                   :name)})

(def ^:private application-column-first-submitted
  {:name :t.applications.export/first-submitted
   :to-value #(->> %
                   :application/first-submitted
                   text/localize-time)})

(def ^:private application-column-state
  {:name :t.applications.export/state
   :to-value #(->> %
                   :application/state
                   text/localize-state)})

(def ^:private application-column-resources
  {:name :t.applications.export/resources
   :to-value #(->> %
                   :application/resources
                   (mapv :catalogue-item/title)
                   (mapv text/localized)
                   (str/join ", "))})

(def ^:private application-columns
  [application-column-id
   application-column-external-id
   application-column-applicant
   application-column-first-submitted
   application-column-state
   application-column-resources])

(defn- application-to-row [form-id application]
  (concat (for [to-value (mapv :to-value application-columns)]
            (to-value application))
          (select [:application/forms ALL #(= form-id (:form/id %)) :form/fields ALL :field/value] application)))

(defn- form-field-names [form-id application]
  (let [form (first (filter #(= form-id (:form/id %)) (:application/forms application)))]
      (assert form
              (str "Form " form-id " not found in application " (:application/id application)))
      (->> form
           :form/fields
           (mapv :field/title)
           (mapv text/localized))))

(defn applications-to-csv [applications form-id user-id & {:keys [include-drafts]}]
  (let [language (:language (user-settings/get-user-settings user-id))
        applications (filter #(or include-drafts
                                  (not= (:application/state %) :application.state/draft))
                             applications)]
    (if (empty? applications)
      ""
      (text/with-language language
        #(print-to-csv :column-names (concat (mapv (comp text/text :name) application-columns)
                                             (form-field-names form-id (first applications)))
                       :rows (mapv (partial application-to-row form-id) applications)
                       :quote-strings? true)))))

(defn applications-filename []
  (format "applications_%s.csv" (str/replace (text/localize-time (time/now)) " " "_")))

;; Export entitlements

(defn- entitlement-to-row [entitlement]
  [(:resid entitlement)
   (:catappid entitlement)
   (:userid entitlement)
   (text/localize-time (:start entitlement))])

;; TODO: Consider quoting strings to unify with exporting of applications.
(defn entitlements-to-csv [entitlements]
  (print-to-csv :column-names ["resource" "application" "user" "start"]
                :rows (mapv entitlement-to-row entitlements)))
