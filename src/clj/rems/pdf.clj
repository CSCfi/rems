(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [clj-time.core :as time]
            [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [build-index]]
            [rems.context :as context]
            [rems.text :refer [localized localize-decision localize-event localize-state localize-time text with-language]]
            [rems.util :refer [getx]])
  (:import [java.io ByteArrayOutputStream]))

(defn- render-user [user]
  (str (or (:name user)
           (:userid user))
       " (" (:userid user) ")"
       " <" (:email user) ">"))

(def heading-style {:spacing-before 20})

(defn- render-header [application]
  (let [state (getx application :application/state)
        resources (getx application :application/resources)]
    (list
     [:heading heading-style
      (str (text :t.applications/application)
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
     [:heading heading-style (text :t.applicant-info/applicants)]
     [:paragraph (text :t.applicant-info/applicant) ": " (render-user (getx application :application/applicant))]
     (doall
      (for [member (getx application :application/members)]
        [:paragraph (text :t.applicant-info/member) ": " (render-user member)]))
     [:heading heading-style (text :t.form/resources)]
     [:list
      (doall
       (for [resource resources]
         [:phrase
          (localized (:catalogue-item/title resource))
          " (" (:resource/ext-id resource) ")"]))])))

(defn- attachment-filenames [application]
  (build-index {:keys [:attachment/id] :value-fn :attachment/filename} (:application/attachments application)))

(defn- render-events [application]
  (let [filenames (attachment-filenames application)
        events (getx application :application/events)]
    (list
     [:heading heading-style (text :t.form/events)]
     (if (empty? events)
       [:paragraph "–"]
       [:list
        (doall
         (for [event events
               :when (not (#{:application.event/draft-saved} (:event/type event)))]
           [:phrase
            (localize-time (:event/time event))
            " "
            (localize-event event)
            (when-let [decision (localize-decision event)]
              (str "\n" decision))
            (let [comment (get event :application/comment)]
              (when-not (empty? comment)
                (str "\n"
                     (text :t.form/comment)
                     ": "
                     comment)))
            (when-let [attachments (seq (get event :event/attachments))]
              (str "\n"
                   (text :t.form/attachments)
                   ": "
                   (str/join ", " (map (comp filenames :attachment/id) attachments))))]))]))))

(defn- field-value [filenames field]
  (let [value (:field/value field)]
    (case (:field/type field)
      (:option :multiselect)
      (localized (get (build-index {:keys [:key] :value-fn :label} (:field/options field)) value))

      :attachment
      (if (empty? value)
        value
        (->> value
             (form/parse-attachment-ids)
             (map filenames)
             (str/join ", ")))

      :table
      (let [columns (:field/columns field)]
        (into [:table {:header (vec (for [column columns] (localized (:label column))))}]
              (for [row (:field/value field)]
                (let [values (build-index {:keys [:column] :value-fn :value} row)]
                  (vec (for [column columns]
                         (get values (:key column))))))))

      (:field/value field))))

(def label-field-style {:spacing-before 8})
(def header-field-style {:spacing-before 8 :style :bold :size 15})
(def field-style {:spacing-before 8 :style :bold})

(defn- render-field [filenames field]
  (when (:field/visible field)
    (list
     [:paragraph (case (:field/type field)
                   :label label-field-style
                   :header header-field-style
                   field-style)
      (localized (:field/title field))]
     [:paragraph (field-value filenames field)])))

(defn- render-fields [application]
  (let [filenames (attachment-filenames application)]
    (doall
     (for [form (getx application :application/forms)]
       (list [:heading heading-style (or (get-in form [:form/external-title context/*lang*]) (text :t.form/application))]
             (doall (for [field (getx form :form/fields)]
                      (render-field filenames field))))))))

(defn- render-license [license]
  ;; TODO license text?
  ;; TODO get acceptance state?
  [:paragraph
   (localized (:license/title license))])

(defn- render-licenses [application]
  (list [:heading heading-style (text :t.form/licenses)]
        (doall
         (for [license (getx application :application/licenses)]
           (render-license license)))))

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
