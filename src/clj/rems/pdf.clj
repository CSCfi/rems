(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [rems.text :refer [localized localize-event localize-state localize-time text with-language]])
  (:import [java.io ByteArrayOutputStream]))

(defn- render-header [application]
  (let [state (:application/state application)
        events (:application/events application)
        user (:application/applicant-attributes application)
        resources (:application/resources application)]
    (list
     [:paragraph
      (text :t.applications/state)
      (when state [:phrase ": " (localize-state state)])]
     [:heading (text :t.applicant-info/applicant)]
     [:paragraph (:eppn user)]
     [:paragraph (:mail user)]
     ;; TODO more members
     ;; TODO more fields?
     [:heading (text :t.form/resources)]
     (into
      [:list]
      (for [resource resources]
        [:phrase
         (localized (:catalogue-item/title resource))
         " (" (:resource/ext-id resource) ")"]))
     [:heading (text :t.form/events)]
     (if (empty? events)
       [:paragraph "–"]
       (into
        [:table {:header [(text :t.form/user)
                          (text :t.form/event)
                          (text :t.form/comment)
                          (text :t.form/date)]}]
        (for [event events]
          [(:event/actor event)
           (localize-event (:event/type event))
           (:application/comment event)
           (localize-time (:event/time event))]))))))

(defn- render-field [field]
  (list
   [:heading (localized (:field/title field))]
   [:paragraph (:field/value field)]))

(defn- render-fields [application]
  (apply concat
         (for [field (get-in application [:application/form :form/fields])]
           (render-field field))))

(defn- render-license [license]
  ;; TODO nicer checkbox rendering
  ;; TODO license text?
  [:paragraph
   (if (:license/accepted license) "[x] " "[ ] ")
   (localized (:license/title license))])

(defn- render-licenses [application]
  (into
   [[:heading (text :t.form/licenses)]]
   (for [license (:application/licenses application)]
     (render-license license))))

(defn- render-application [application]
  [{}
   [:heading (text :t.applications/application)]
   (render-header application)
   (render-fields application)
   (render-licenses application)])

(defn application-to-pdf [application out]
  (pdf (render-application application) out))

(defn application-to-pdf-bytes [application]
  (let [out (ByteArrayOutputStream.)]
    (application-to-pdf application out)
    (.toByteArray out)))

(comment
  (with-language :en
    #(clojure.pprint/pprint (render-application application)))
  (with-language :en
    #(application-to-pdf application "/tmp/application.pdf")))
