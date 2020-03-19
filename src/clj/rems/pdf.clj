(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [clj-time.core :as time]
            [rems.text :refer [localized localize-event localize-state localize-time text with-language]]
            [rems.util :refer [getx getx-in]])
  (:import [java.io ByteArrayOutputStream]))

(defn- render-user [user]
  (str (or (:name user)
           (:userid user))
       " (" (:userid user) ")"
       " <" (:email user) ">"))

(defn- render-header [application]
  (let [state (getx application :application/state)
        resources (getx application :application/resources)]
    (concat
     (list
      [:heading (str (text :t.applications/application)
                     " "
                     (get application :application/external-id
                          (getx application :application/id))
                     (when-let [description (get application :application/description)]
                       (str ": " description)))]
      [:paragraph
       (text :t.pdf/generated)
       " "
       (localize-time (time/now))]
      [:paragraph
       (text :t.applications/state)
       (when state [:phrase ": " (localize-state state)])]
      [:heading (text :t.applicant-info/applicants)]
      [:paragraph (text :t.applicant-info/applicant) ": " (render-user (getx application :application/applicant))])
     (seq
      (for [member (getx application :application/members)]
        [:paragraph (text :t.applicant-info/member) ": " (render-user member)]))
     (list
      [:heading (text :t.form/resources)]
      (into
       [:list]
       (for [resource resources]
         [:phrase
          (localized (:catalogue-item/title resource))
          " (" (:resource/ext-id resource) ")"]))))))

(defn- render-events [application]
  (let [events (getx application :application/events)]
    (list
     [:heading (text :t.form/events)]
     (if (empty? events)
       [:paragraph "–"]
       (into
        [:table {:header [(text :t.form/date)
                          (text :t.form/event)
                          (text :t.form/comment)]}]
        (for [event events]
          [(localize-time (:event/time event))
           (localize-event event)
           (get event :application/comment "")]))))))

(defn- field-value [field]
  (case (:field/type field)
    (:option :multiselect)
    (->> (:field/options field)
         (filter #(= (:field/value field) (:key %)))
         first
         :label
         localized)

    (:field/value field)))

(defn- render-field [field]
  (when (:field/visible field)
    (list
     [:paragraph (case (:field/type field)
                   :label {}
                   :header {:style :bold :size 15}
                   {:style :bold})
      (localized (:field/title field))]
     [:paragraph (field-value field)])))

(defn- render-fields [application]
  (apply concat
         (list [:heading (text :t.form/application)])
         (for [form (getx application :application/forms)
               field (getx form :form/fields)]
           (render-field field))))

(defn- render-license [license]
  ;; TODO license text?
  ;; TODO get acceptance state?
  [:paragraph
   (localized (:license/title license))])

(defn- render-licenses [application]
  (concat (list [:heading (text :t.form/licenses)])
          (for [license (getx application :application/licenses)]
            (render-license license))))

(defn- render-application [application]
  [{}
   (render-header application)
   (render-licenses application)
   (render-fields application)
   (render-events application)])

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
