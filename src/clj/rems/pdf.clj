(ns rems.pdf
  "Rendering applications as pdf"
  (:require [clj-pdf.core :refer :all]
            [rems.db.applications :as applications]
            [rems.text :refer [text localize-state with-language]]))

(defn- render-header [form]
  (let [state (get-in form [:application :state])]
    (list
     ;; TODO applied resources
     ;; TODO applicant info
     ;; TODO events
     [:paragraph
      (text :t.applications/state)
      (when state [:phrase ": " (text (localize-state state))])])))

(defn- render-field [field]
  (list
   [:heading (:title field)]
   [:paragraph (:value field)]))

(defn- apply-localization [item language]
  (merge item (get-in item [:localizations language])))

(defn- render-fields [form]
  ;; TODO licenses
  (apply concat
         (for [field (:items form)]
           (render-field (apply-localization field :en)))))

(defn- render-form [form]
  [{}
   (render-header form)
   (render-fields form)])

(defn application-to-pdf [form out]
  (pdf (render-form form) out))

(comment
  (def form
    (binding [rems.context/*user* {"eppn" "developer"}]
      (applications/get-form-for 1)))
  (with-language :en
    #(clojure.pprint/pprint (render-form form)))
  (with-language :en
    #(application-to-pdf form "/tmp/application.pdf")))
