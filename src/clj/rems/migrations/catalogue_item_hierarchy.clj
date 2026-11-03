
(ns rems.migrations.catalogue-item-hierarchy
  "This migration is a no-op going up, because the catalogue item hierarchy
  feature doesn not require database schema changes.

  Migrating down drops the new field `children` in `catalogueitemdata`.
  Otherwise, if any catalogue items are configured with children, having the
  field present would throw an exception once REMS validates the data and
  discovers the 'extra' key.

  Simply removing the association in the UI or API only truncates the field
  contents and the key remains present in `catalogueitemdata`.

  See also [[rems.db.catalogue/CatalogueItemData]]"

  (:require [clj-time.core :as time]
            [clojure.pprint]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [hugsql.core :as hugsql]
            [medley.core]
            [rems.db.core :as db]
            [rems.json :as json]))

(hugsql/def-db-fns-from-string
  "
-- :name get-catalogue-items-with-children :*
SELECT id, catalogueitemdata::TEXT
FROM catalogue_item
WHERE catalogueitemdata::jsonb->'children' IS NOT NULL

-- :name drop-catalogue-item-children! :!
UPDATE catalogue_item SET catalogueitemdata = :catalogueitemdata::jsonb WHERE id = :id;
")

(defn migrate-up [_config]
  ;; no-op
  nil)

(defn migrate-drop-children
  "Remove `children` from `catalogueitemdata` of the supplied `catalogue-item`"
  [{:keys [id catalogueitemdata] :as catalogue-item}]
  {:pre [(pos-int? id)]
   :post [(= id (:id %))]}
  (if catalogueitemdata
    (update catalogue-item
            :catalogueitemdata
            (fn dissoc-children [catalogue-item-data]
              (-> catalogue-item-data
                  json/parse-string
                  (dissoc :children)
                  json/generate-string)))
    catalogue-item))

(deftest test-migrate-drop-children
  (testing "keeps categories, drops children"
    ;; the query only picks up records with `children` set but for the sake of
    ;; completeness, test all possible values of `catalogueitemdata`
    (let [cat-1 {:id 1 :catalogueitemdata (json/generate-string {:categories [] :children [{:catalogue-item/id 2}]})}
          cat-2 {:id 2 :catalogueitemdata (json/generate-string {})}
          cat-3 {:id 3 :catalogueitemdata (json/generate-string {:categories [{:category/id 1}]})}
          cat-4 {:id 4 :catalogueitemdata (json/generate-string {:children [{:catalogue-item/id 5}]})}
          cat-5 {:id 5 :resid 1 :formid 1 :organization "csc"}]
      (is (= {:id 1, :catalogueitemdata "{\"categories\":[]}"}
             (migrate-drop-children cat-1))
          "with empty categories, non-empty children")
      (is (= {:id 2, :catalogueitemdata "{}"}
             (migrate-drop-children cat-2))
          "with empty catalogue item data")
      (is (= {:id 3, :catalogueitemdata "{\"categories\":[{\"category/id\":1}]}"}
             (migrate-drop-children cat-3))
          "with no children")
      (is (= {:id 4, :catalogueitemdata "{}"}
             (migrate-drop-children cat-4))
          "with no categories")
      (is (= {:id 5 :resid 1 :formid 1 :organization "csc"}
             cat-5
             (migrate-drop-children cat-5))
          "nothing gets changed or added if catalogueitemdata doesn't exist"))))

(defn migrate-down [{:keys [conn]}]
  (let [backup-file (str "target/" (time/now) "-catalogue-items-backup.edn")]
    (when-let [items (seq (get-catalogue-items-with-children conn))]
      (log/info "Writing backup in" backup-file)
      (spit backup-file
            (with-out-str (clojure.pprint/write items :dispatch clojure.pprint/code-dispatch))
            :append true)
      (log/info "Migrating down" (count items) "catalogue items...")

      (doseq [catalogue-item items
              :let [{:keys [id catalogueitemdata]} (migrate-drop-children catalogue-item)]]
        (log/info "removing hierarchy of catalogue item id:" id)
        (drop-catalogue-item-children! conn {:id id
                                             :catalogueitemdata catalogueitemdata})))))
(comment
  (migrate-down {:conn db/*db*})

  ;; restoring the removed hierarchy
  (require 'clojure.edn)
  (require 'rems.db.catalogue)
  (let [filename "target/1970-01-01T00:00:00.000Z-catalogue-items-backup.edn" ; replace with actual file
        items (clojure.edn/read-string (slurp filename))]
    (doseq [item items
            :let [{:keys [id catalogueitemdata]} (update item :catalogueitemdata json/parse-string)]]
      (log/info "restoring catalogue-item-data of catalogue item" id)
      (rems.db.catalogue/edit-catalogue-item! id
                                              (medley.core/assoc-some {}
                                                                      :children (:children catalogueitemdata)
                                                                      :categories (:categories catalogueitemdata))))))
