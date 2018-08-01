(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.text :refer [text localize-event localize-state localize-time with-language]]))

(defn- render-header [form]
  (let [state (get-in form [:application :state])
        events (get-in form [:application :events])
        user (:applicant-attributes form)
        catalogue-items (:catalogue-items form)]
    (list
     [:paragraph
      (text :t.applications/state)
      (when state [:phrase ": " (text (localize-state state))])]
     [:heading (text :t.applicant-info/applicant)]
     [:paragraph (get user "eppn")]
     [:paragraph (get user "mail")]
     ;; TODO more fields?
     [:heading (text :t.form/resources)]
     (into
      [:list]
      (for [ci catalogue-items]
        [:phrase
         (get-in ci [:localizations context/*lang* :title])
         " (" (:resid ci) ")"]))
     [:heading (text :t.form/events)]
     (into
      [:table {:header [(text :t.form/user) (text :t.form/event) (text :t.form/comment) (text :t.form/date)]}]
      (for [e events]
        [(:userid e) (text (localize-event (:event e))) (:comment e) (localize-time (:time e))])))))

(defn- render-field [field]
  (list
   [:heading (:title field)]
   [:paragraph (:value field)]))

(defn- apply-localization [item]
  (merge item (get-in item [:localizations context/*lang*])))

(defn- render-fields [form]
  (apply concat
         (for [field (:items form)]
           (render-field (apply-localization field)))))

(defn- render-license [license]
  ;; TODO nicer checkbox rendering
  ;; TODO license text?
  [:paragraph
   (if (:approved license) "[x]" "[ ]")
   (:title license)])

(defn- render-licenses [form]
  (into
   [[:heading (text :t.form/licenses)]]
   (for [license (:licenses form)]
     (render-license (apply-localization license)))))

(defn- render-form [form]
  [{}
   (render-header form)
   (render-fields form)
   (render-licenses form)])

(defn application-to-pdf [form out]
  (pdf (render-form form) out))

(comment
  (def form
    (binding [rems.context/*user* {"eppn" "developer"}]
      (applications/get-form-for 9)))
  (with-language :en
    #(clojure.pprint/pprint (render-form form)))
  (with-language :en
    #(application-to-pdf form "/tmp/application.pdf")))
