(ns rems.migrations.missing-form-templates
  (:require [clojure.test :refer :all]
            [cprop.tools :refer [merge-maps]]
            [hugsql.core :as hugsql]
            [rems.json :as json]))

;; SQL queries repeated here so that this migration is standalone

(hugsql/def-db-fns-from-string
  "
-- :name get-forms :? :*
SELECT
  id,
  owneruserid,
  modifieruserid,
  title,
  visibility,
  start,
  endt as \"end\",
  organization,
  enabled,
  archived
FROM application_form;

-- :name get-form-template :? :1
SELECT
  id,
  organization,
  title,
  start,
  endt as \"end\",
  fields::TEXT,
  enabled,
  archived
FROM form_template
WHERE id = :id;

-- :name save-form-template! :insert
INSERT INTO form_template
(id, ownerUserId, modifierUserId, title, visibility, start, endt, organization, enabled, archived, fields)
VALUES
(:id,
 :owneruserid,
 :modifieruserid,
 :title,
 :visibility::SCOPE,
 :start,
 :end,
 :organization,
 :enabled,
 :archived,
 :fields::jsonb
);

-- :name get-form-items :? :*
SELECT
  item.id,
  formitemoptional,
  type,
  value,
  itemorder,
  item.visibility,
  itemmap.maxlength
FROM application_form form
LEFT OUTER JOIN application_form_item_map itemmap ON form.id = itemmap.formId
LEFT OUTER JOIN application_form_item item ON item.id = itemmap.formItemId
WHERE form.id = :id AND item.id IS NOT NULL
ORDER BY itemorder;

-- :name get-form-item-localizations :? :*
SELECT langCode, title, inputprompt
FROM application_form_item_localization
WHERE itemId = :item;

-- :name get-form-item-options :? :*
SELECT itemId, key, langCode, label, displayOrder
FROM application_form_item_options
WHERE itemId = :item;

")

(defn process-field-options [options]
  (->> options
       (map (fn [{:keys [key langcode label displayorder]}]
              {:key key
               :label {(keyword langcode) label}
               :displayorder displayorder}))
       (group-by :key)
       (map (fn [[_key options]] (apply merge-maps options))) ; merge label translations
       (sort-by :displayorder)
       (mapv #(select-keys % [:key :label]))))

(deftest process-field-options-test
  (is (= [{:key "yes" :label {:en "Yes" :fi "Kyllä"}}
          {:key "no" :label {:en "No" :fi "Ei"}}]
         (process-field-options
          [{:itemid 9, :key "no", :langcode "en", :label "No", :displayorder 1}
           {:itemid 9, :key "no", :langcode "fi", :label "Ei", :displayorder 1}
           {:itemid 9, :key "yes", :langcode "en", :label "Yes", :displayorder 0}
           {:itemid 9, :key "yes", :langcode "fi", :label "Kyllä", :displayorder 0}]))))

(defn process-localizations [localizations]
  (reduce merge-maps (for [{:keys [langcode title inputprompt]} localizations]
                       (do (when (or title inputprompt)
                             (assert langcode "Missing langcode in application_form_item_localization table!"))
                           (merge (when title {:title {(keyword langcode) title}})
                                  (when inputprompt {:input-prompt {(keyword langcode) inputprompt}}))))))

(deftest process-localizations-test
  (is (= {:title {:fi "fi title" :en "en title" :es "es title"}
          :input-prompt {:fi "fi prompt" :en "en prompt" :es "es prompt"}}
         (process-localizations [{:langcode "fi" :title "fi title" :inputprompt "fi prompt"}
                                 {:langcode "en" :title "en title" :inputprompt "en prompt"}
                                 {:langcode "es" :title "es title" :inputprompt "es prompt"}])))
  (is (= {:title {:fi "fi title" :en "en title"}}
         (process-localizations [{:langcode "fi" :title "fi title" :inputprompt nil}
                                 {:langcode "en" :title "en title" :inputprompt nil}]))))


(defn process-field
  "Returns a field structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :inputprompt \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [field options localizations]
  (merge {:id (:id field)
          :optional (:formitemoptional field)
          :type (:type field)}
         (when-let [maxlength (:maxlength field)]
           {:maxlength maxlength})
         (when (seq options)
           {:options (process-field-options options)})
         (process-localizations localizations)))

(deftest process-field-test
  (is (= {:type "option",
          :title {:fi "Projektitiimin koko", :en "Project team size"},
          :id 5,
          :optional true,
          :options
          [{:key "1-5", :label {:fi "1-5 henkilöä", :en "1-5 persons"}}
           {:key "6-20", :label {:fi "6-20 henkilöä", :en "6-20 persons"}}
           {:key "20+",
            :label {:fi "yli 20 henkilöä", :en "over 20 persons"}}]}
         (process-field {:id 5,
                         :formitemoptional true,
                         :type "option",
                         :value 0,
                         :itemorder 4,
                         :visibility "public",
                         :maxlength nil}
                        [{:itemid 5, :key "1-5", :langcode "en", :label "1-5 persons", :displayorder 0}
                         {:itemid 5, :key "1-5", :langcode "fi", :label "1-5 henkilöä", :displayorder 0}
                         {:itemid 5, :key "20+", :langcode "en", :label "over 20 persons", :displayorder 2}
                         {:itemid 5, :key "20+", :langcode "fi", :label "yli 20 henkilöä", :displayorder 2}
                         {:itemid 5, :key "6-20", :langcode "en", :label "6-20 persons", :displayorder 1}
                         {:itemid 5, :key "6-20", :langcode "fi", :label "6-20 henkilöä", :displayorder 1}]
                        [{:langcode "en", :title "Project team size", :inputprompt nil}
                         {:langcode "fi", :title "Projektitiimin koko", :inputprompt nil}])))
  (is (= {:type "description",
          :title {:fi "Projektin nimi", :en "Project name"},
          :input-prompt {:fi "Projekti", :en "Project"},
          :id 1,
          :optional false}
         (process-field {:id 1,
                         :formitemoptional false,
                         :type "description",
                         :value 0,
                         :itemorder 0,
                         :visibility "public",
                         :maxlength nil}
                        []
                        [{:langcode "en", :title "Project name", :inputprompt "Project"}
                         {:langcode "fi", :title "Projektin nimi", :inputprompt "Projekti"}])))
  (is (= {:type "texta",
          :maxlength 100,
          :title {:fi "Tutkimussuunnitelma", :en "Research plan"},
          :id 8,
          :optional true}
         (process-field {:id 8,
                         :formitemoptional true,
                         :type "texta",
                         :value 0,
                         :itemorder 7,
                         :visibility "public",
                         :maxlength 100}
                        []
                        [{:langcode "en", :title "Research plan", :inputprompt nil}
                         {:langcode "fi", :title "Tutkimussuunnitelma", :inputprompt nil}]))))



(defn get-fields [conn params]
  (vec (for [field (get-form-items conn params)]
         (process-field field
                        (get-form-item-options conn {:item (:id field)})
                        (get-form-item-localizations conn {:item (:id field)})))))

(defn migrate-up [{:keys [conn]}]
  (doseq [form (get-forms conn)]
    (let [id (:id form)
          template (get-form-template conn {:id id})]
      (when (nil? template)
        (let [fields (get-fields conn {:id id})]
          (save-form-template! conn (assoc form :fields (json/generate-string fields))))))))

(comment
  (migrate-up {:conn rems.db.core/*db*}))
