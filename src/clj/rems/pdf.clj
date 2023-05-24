(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [clj-time.core :as time]
            [clojure.string :as str]
            [rems.common.application-util :as application-util]
            [rems.common.form :as form]
            [rems.common.util :refer [build-index]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.text :refer [localized localize-decision localize-event localize-attachment localize-state localize-time text text-format with-language]]
            [rems.util :refer [getx]])
  (:import [java.io ByteArrayOutputStream]))

(def heading-style {:spacing-before 20})
(def field-heading-style {:spacing-before 8 :style :bold})
(def field-style {:spacing-before 8})

(defn- render-header [application]
  (let [state (getx application :application/state)]
    (list
     [:heading heading-style
      (str (text :t.applications/application)
           " "
           (get application :application/external-id
                (getx application :application/id))
           (when-let [description (get application :application/description)]
             (str ": " description)))]
     [:paragraph field-style
      (text :t.pdf/generated)
      " "
      (localize-time (time/now))]
     [:paragraph
      (text :t.applications/state)
      (when state [:phrase ": " (localize-state state)])])))

(defn- render-user [application user label]
  (let [userid (:userid user)
        email (:email user)]
    (list
     [:paragraph field-style
      [:phrase {:style :bold} label] ": " (if-let [name (:name user)]
                                            (str name " (" userid ") <" email ">")
                                            (str userid " <" email ">"))]
     (when (some? userid)
       [:paragraph
        (text-format :t.label/default
                     (text :t.form/accepted-licenses)
                     (if (application-util/accepted-licenses? application userid)
                       (text :t.form/checkbox-checked)
                       (text :t.form/checkbox-unchecked)))]))))

(defn- render-applicants [application]
  (list
   [:heading heading-style (text :t.applicant-info/applicants)]
   (render-user application
                (getx application :application/applicant)
                (text :t.applicant-info/applicant))
   (doall
    (for [member (getx application :application/members)]
      (render-user application member (text :t.applicant-info/member))))))

(defn- render-duo [duo & [opts]]
  (let [label (text-format :t.label/dash
                           (:shorthand duo)
                           (str/capitalize (localized (:label duo))))]
    [:paragraph (:field-style opts)
     [:paragraph (:label-style opts) label]
     [:list
      (doall
       (for [restriction (:restrictions duo)
             value (:values restriction)]
         [:phrase (case (:type restriction)
                    :mondo (text-format :t.label/dash
                                        (:id value)
                                        (str/capitalize (:label value)))
                    (:value value))]))]]))

(defn- render-resources [application]
  (let [resources (getx application :application/resources)]
    (list
     [:heading heading-style (text :t.form/resources)]
     (doall
      (for [resource resources
            :let [title (localized (:catalogue-item/title resource))
                  ext-id (:resource/ext-id resource)
                  duos (get-in resource [:resource/duo :duo/codes])]]
        (list
         [:paragraph field-heading-style
          (text-format :t.label/parens title ext-id)]
         (when (seq duos)
           (list
            [:paragraph field-style (text :t.duo/title)]
            (doall
             (for [duo duos]
               (render-duo duo {:field-style {:spacing-before 8}})))))))))))

(defn- render-duos [application]
  (when-some [duos (seq (get-in application [:application/duo :duo/codes]))]
    (concat
     (list [:heading heading-style (text :t.duo/title)]
           [:paragraph field-style] ; for margin
           (render-duo (first duos) {:label-style {:style :bold}}))
     (doall
      (for [duo (rest duos)]
        (render-duo duo {:field-style {:spacing-before 8}
                         :label-style {:style :bold}}))))))

(defn- render-license [license]
  (list [:paragraph field-heading-style (localized (:license/title license))]
        (case (:license/type license)
          :text
          [:paragraph (localized (:license/text license))]
          :link
          [:paragraph (localized (:license/link license))]
          :attachment
          [:paragraph (localized (:license/attachment-filename license))])))

(defn- render-licenses [application]
  (list [:heading heading-style (text :t.form/licenses)]
        (doall
         (for [license (getx application :application/licenses)]
           (render-license license)))))

(defn- attachment-filenames [application]
  (->> (:application/attachments application)
       (build-index {:keys [:attachment/id]
                     :value-fn localize-attachment})))

(defn- field-value [filenames field]
  (let [value (:field/value field)]
    (case (:field/type field)
      :option
      (localized (-> (build-index {:keys [:key] :value-fn :label}
                                  (:field/options field))
                     (get value)))

      :multiselect
      (let [options (build-index {:keys [:key] :value-fn :label}
                                 (:field/options field))
            values (form/parse-multiselect-values value)]
        (->> (sort values) ; parse returns set which can be in random order
             (map (partial get options))
             (map localized)
             (str/join ", ")))

      :attachment
      (if (empty? value)
        value
        (->> value
             (form/parse-attachment-ids)
             (map filenames)
             (str/join ", ")))

      :table
      (if-let [rows (seq (:field/value field))]
        (let [columns (:field/columns field)]
          (into [:table {:header (vec (for [column columns] (localized (:label column))))}]
                (for [row rows]
                  (let [values (build-index {:keys [:column] :value-fn :value} row)]
                    (vec (for [column columns]
                           (get values (:key column))))))))
        (text :t.form/no-rows))

      (:field/value field))))

(defn- render-field [filenames field]
  (when (:field/visible field)
    (list
     [:paragraph (case (:field/type field)
                   :header (merge field-heading-style {:size 15})
                   field-heading-style)
      (localized (:field/title field))]
     [:paragraph (field-value filenames field)])))

(defn- render-fields [application]
  (let [filenames (attachment-filenames application)]
    (doall
     (for [form (getx application :application/forms)
           :let [fields (->> (getx form :form/fields)
                             (remove :field/private))]
           :when (seq fields)]
       (list [:heading heading-style (or (get-in form [:form/external-title context/*lang*])
                                         (text :t.form/application))]
             (doall (for [field fields]
                      (render-field filenames field))))))))

(defn- render-events [application]
  (let [filenames (attachment-filenames application)
        events (getx application :application/events)]
    (list
     [:heading heading-style (text :t.form/events)]
     [:paragraph field-style
      (if (empty? events)
        ""
        [:list
         (doall
          (for [event events
                :when (not (#{:application.event/draft-saved}
                            (:event/type event)))]
            [:phrase
             (localize-time (:event/time event)) " " (localize-event event)
             (when-let [decision (localize-decision event)]
               (str "\n" decision))
             (when-some [comment (not-empty (:application/comment event))]
               (str "\n" (text-format :t.label/default
                                      (text :t.form/comment)
                                      comment)))
             (when-some [attachments (seq (:event/attachments event))]
               (str "\n" (text-format :t.label/default
                                      (text :t.form/attachments)
                                      (->> attachments
                                           (map (comp filenames :attachment/id))
                                           (str/join ", ")))))]))])])))

(defn- render-application [application]
  [(env :pdf-metadata)
   (render-header application)
   (render-applicants application)
   (when (:show-resources-section env)
     (render-resources application))
   (render-duos application)
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
