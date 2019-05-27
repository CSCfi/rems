(ns rems.migrations.form-templates-for-old-forms
  (:require [clojure.test :refer :all]
            [hugsql.core :as hugsql]
            [rems.json :as json]))

;; SQL queries repeated here so that this migration is standalone

(hugsql/def-db-fns-from-string
  "
-- :name get-forms :? :*
SELECT id FROM application_form;

-- :name get-form-template-impl :? :1
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

-- :name set-template-fields! :!
UPDATE form_template
SET fields = :fields::jsonb
WHERE id = :id;

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
ORDER BY itemorder;")

(defn get-template-fields [db params]
  (-> (get-form-template-impl db params)
      :fields
      json/parse-string))

(defn should-update? [fields]
  (not (every? #(contains? % :id) fields)))

(defn update-fields [template-fields items]
  (assert (= (count template-fields)
             (count items)))
  (let [result (mapv #(assoc %2 :id (:id %1)) items template-fields)]
    (assert (not (should-update? result)))
    result))

(deftest test-update-template
  (is (= [{:type "description"
           :title {:fi "Projektin nimi" :en "Project name"}
           :input-prompt {:fi "Projekti" :en "Project"}
           :id 1
           :optional false}
          {:type "texta"
           :title {:fi "Projektin tarkoitus" :en "Purpose of the project"}
           :input-prompt
           {:fi "Projektin tarkoitus on..."
            :en "The purpose of the project is to..."}
           :id 7
           :optional false}
          {:type "date"
           :title
           {:fi "Projektin aloituspäivä" :en "Start date of the project"}
           :id 3
           :optional true}]
         (update-fields
          [{:type "description"
            :title {:fi "Projektin nimi" :en "Project name"}
            :input-prompt {:fi "Projekti" :en "Project"}
            :optional false}
           {:type "texta"
            :title {:fi "Projektin tarkoitus" :en "Purpose of the project"}
            :input-prompt
            {:fi "Projektin tarkoitus on..."
             :en "The purpose of the project is to..."}
            :optional false}
           {:type "date"
            :title
            {:fi "Projektin aloituspäivä" :en "Start date of the project"}
            :optional true}]
          [{:id 1} {:id 7} {:id 3}]))))

(defn migrate-up [{:keys [conn]}]
  (doseq [{:keys [id]} (get-forms conn)]
    (let [fields (get-template-fields conn {:id id})]
      (when (should-update? fields)
        (let [items (get-form-items conn {:id id})
              updated (update-fields fields items)]
          (set-template-fields! conn {:id id :fields (json/generate-string updated)}))))))
