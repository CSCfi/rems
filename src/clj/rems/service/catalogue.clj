(ns rems.service.catalogue
  (:require [clojure.set]
            [medley.core :refer [assoc-some remove-vals update-existing]]
            [rems.common.roles :as roles]
            [rems.common.util :refer [apply-filters build-dags]]
            [rems.db.applications]
            [rems.db.catalogue]
            [rems.db.category]
            [rems.db.form]
            [rems.db.organizations]
            [rems.db.resource]
            [rems.db.workflow]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]))

(defn create-catalogue-item! [{:keys [archived categories enabled form localizations organization resid start wfid] :as command}]
  (util/check-allowed-organization! organization)
  (let [id (rems.db.catalogue/create-catalogue-item!
            (-> {:organization-id (:organization/id organization "default")}
                (assoc-some :archived archived
                            :categories categories
                            :enabled enabled
                            :form-id form
                            :localizations localizations
                            :resource-id resid
                            :start start
                            :workflow-id wfid)))]
    {:success true
     :id id}))

(defn- join-dependencies [item & [opts]]
  (let [join-organization? (:join-organization? opts true)
        expand-names? (:expand-names? opts false)
        resource (rems.db.resource/get-resource (:resource-id item))]

    (cond-> item
      true (update-existing :categories rems.db.category/enrich-categories)
      true (assoc :resid (:resid resource))
      join-organization? rems.db.organizations/join-organization

      expand-names?
      (assoc :form-name (:form/internal-name (rems.db.form/get-form-template (:formid item)))
             :resource-name (:resid resource)
             :workflow-name (:title (rems.db.workflow/get-workflow (:wfid item)))))))

(defn- query-filter [{:keys [archived enabled expired resource]}]
  (let [filters {:archived archived
                 :enabled enabled
                 :expired expired
                 :resid resource}]
    (apply-filters (remove-vals nil? filters))))

(defn get-catalogue-items
  {:arglists '([& [{:keys [archived enabled expand-names? expired join-organization? resource]}]])}
  [& [opts]]
  (->> (rems.db.catalogue/get-catalogue-items)
       (eduction (map #(join-dependencies % opts))
                 (query-filter (merge {:archived false}
                                      opts
                                      (when-not (apply roles/has-roles? roles/+admin-read-roles+)
                                        ;; only admins get enabled and disabled items
                                        {:enabled true}))))))

(defn get-catalogue-item
  {:arglists '([id & [{:keys [expand-names? join-organization?]}]])}
  [id & [opts]]
  (when-let [item (rems.db.catalogue/get-catalogue-item id)]
    (when (or (:enabled item)
              (apply roles/has-roles? roles/+admin-read-roles+)) ; only admins get enabled and disabled items
      (join-dependencies item opts))))

(defn get-catalogue-table
  {:arglists '([& [{:keys [join-organization?]}]])}
  [& [opts]]
  (get-catalogue-items opts))

(defn get-catalogue-tree
  {:arglists '([& [{:keys [archived empty enabled expand-names? expired join-organization? resource]}]])}
  [& [query-params]]
  (let [catalogue-items (get-catalogue-items query-params)
        has-category? (fn [item category]
                        (contains? (set (map :category/id (:categories item)))
                                   (:category/id category)))
        categories (for [category (rems.db.category/get-categories)
                         :let [matching-items (filterv #(has-category? % category) catalogue-items)]]
                     (assoc-some category :category/items matching-items))
        include-category? (fn [category]
                            (case (:empty query-params)
                              nil true ; not set, include all
                              false (or (seq (:category/children category))
                                        (seq (:category/items category)))
                              true (and (empty? (:category/children category))
                                        (empty? (:category/items category)))))
        items-without-category (for [item catalogue-items
                                     :when (empty? (:categories item))]
                                 item)
        top-level-categories (build-dags {:id-fn :category/id
                                          :child-id-fn :category/id
                                          :children-fn :category/children
                                          :filter-fn include-category?}
                                         categories)]
    {:roots (into (vec top-level-categories)
                  items-without-category)}))

(defn- check-allowed-to-edit! [id]
  (-> id
      get-catalogue-item
      :organization
      util/check-allowed-organization!))

(defn edit-catalogue-item! [{:keys [id localizations organization] :as item}]
  (check-allowed-to-edit! id)
  (when (:organization/id organization)
    (util/check-allowed-organization! organization))
  (rems.db.catalogue/edit-catalogue-item! id {:categories (:categories item)
                                              :localizations localizations
                                              :organization-id (:organization/id organization)})
  (rems.db.applications/reload-applications! {:by-catalogue-item-ids [id]})
  {:success true})

(defn set-catalogue-item-enabled! [{:keys [id enabled]}]
  (check-allowed-to-edit! id)
  (rems.db.catalogue/set-attributes! id {:enabled enabled})
  (rems.db.applications/reload-applications! {:by-catalogue-item-ids [id]})
  {:success true})

(defn set-catalogue-item-archived! [{:keys [id archived]}]
  (check-allowed-to-edit! id)
  (or (dependencies/change-archive-status-error archived {:catalogue-item/id id})
      (do (rems.db.catalogue/set-attributes! id {:archived archived})
          (rems.db.applications/reload-applications! {:by-catalogue-item-ids [id]})
          {:success true})))

(defn change-form!
  "Changes the form of a catalogue item.

  Since we don't want to modify the old item we must create
  a new item that is the copy of the old item except for the changed form."
  [item form-id]
  (util/check-allowed-organization! (:organization item))
  (if (= (:formid item) form-id)
    {:success true :catalogue-item-id (:id item)}
    ;; create a new item with the new form
    (let [new-item-id (rems.db.catalogue/create-catalogue-item!
                       {:archived false
                        :categories (:categories item)
                        :enabled true
                        :form-id form-id
                        :localizations (:localizations item)
                        :organization-id (get-in item [:organization :organization/id])
                        :resource-id (:resource-id item)
                        :workflow-id (:wfid item)})]

      ;; hide the old catalogue item
      (rems.db.catalogue/set-attributes! (:id item) {:archived true :enabled false})

      ;; notify applications with old catalogue item
      (rems.db.applications/reload-applications! {:by-catalogue-item-ids [(:id item)]})

      {:success true :catalogue-item-id new-item-id})))

(defn update!
  "Updates the catalogue item `item`.

  Changes the form and/or workflow.

  Since we don't want to modify the old item we must create
  a new item that is the copy of the old item except for the changed details."
  [item {:keys [form-id workflow-id] :or {form-id :do-not-change-form workflow-id :do-not-change-workflow}}]
  (util/check-allowed-organization! (:organization item))
  ;; are we done already? could be retry
  (if (and (or (= :do-not-change-form form-id)
               (= (:formid item) form-id))
           (or (= :do-not-change-workflow workflow-id)
               (= (:wfid item) workflow-id)))
    {:success true :catalogue-item-id (:id item)}

    ;; create a new item with the new form
    (let [form (case form-id
                 :do-not-change-form (:formid item) ; preserve old
                 nil nil ; form is optional and can be unset
                 form-id)
          wfid (case workflow-id
                 :do-not-change-workflow (:wfid item) ; preserve old
                 workflow-id)
          new-item-id (rems.db.catalogue/create-catalogue-item!
                       {:archived false
                        :categories (:categories item)
                        :enabled true
                        :form-id form
                        :localizations (:localizations item)
                        :organization-id (get-in item [:organization :organization/id])
                        :resource-id (:resource-id item)
                        :workflow-id wfid})]

      ;; hide the old catalogue item
      (rems.db.catalogue/set-attributes! (:id item) {:archived true :enabled false})

      ;; notify applications with old catalogue item
      (rems.db.applications/reload-applications! {:by-catalogue-item-ids [(:id item)]})

      {:success true :catalogue-item-id new-item-id})))
