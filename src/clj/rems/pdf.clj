(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [rems.text :refer [localized localize-event localize-state localize-time text with-language]]
            [rems.util :refer [getx getx-in]])
  (:import [java.io ByteArrayOutputStream]))

(defn- render-header [application]
  (let [state (getx application :application/state)
        events (getx application :application/events)
        user (getx application :application/applicant)
        resources (getx application :application/resources)]
    (list
     [:paragraph
      (text :t.applications/state)
      (when state [:phrase ": " (localize-state state)])]
     [:heading (text :t.applicant-info/applicant)]
     [:paragraph (get user :name "-")]
     [:paragraph (getx user :userid)]
     [:paragraph (get user :email "-")]
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
           (localize-event event)
           (get event :application/comment "")
           (localize-time (:event/time event))]))))))

(defn- render-field [field]
  (list
   [:heading (localized (:field/title field))]
   [:paragraph (:field/value field)]))

(defn- render-fields [application]
  (seq ;; TODO clj-pdf doesn't tolerate empty sequences
   (apply concat
          (for [field (getx-in application [:application/form :form/fields])]
            (render-field field)))))

(defn- render-license [license]
  ;; TODO nicer checkbox rendering
  ;; TODO license text?
  [:paragraph
   ;; TODO get acceptance state
   #_(if (getx license :license/accepted) "[x] " "[ ] ")
   (localized (:license/title license))])

(defn- render-licenses [application]
  (concat (list [:heading (text :t.form/licenses)])
          (for [license (getx application :application/licenses)]
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
