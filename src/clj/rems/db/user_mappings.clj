(ns rems.db.user-mappings
  (:require [clojure.string :as str]
            [rems.cache :as cache]
            [rems.common.util :refer [apply-filters]]
            [rems.db.core :as db]
            [schema.core :as s]))

(s/defschema UserMappings
  {:userid s/Str
   :ext-id-attribute s/Str
   :ext-id-value s/Str})

(def ^:private validate-user-mapping
  (s/validator UserMappings))

(defn- format-user-mapping [mapping]
  {:userid (:userid mapping)
   :ext-id-attribute (:extidattribute mapping)
   :ext-id-value (:extidvalue mapping)})

(declare by-extidattribute
         by-extidvalue)

(def user-mappings-cache
  (cache/basic {:id ::user-mappings-cache
                :miss-fn (fn [userid]
                           (->> (db/get-user-mappings {:userid userid})
                                (mapv format-user-mapping)
                                (mapv validate-user-mapping)))
                :reload-fn (fn []
                             (->> (db/get-user-mappings {})
                                  (mapv format-user-mapping)
                                  (mapv validate-user-mapping)
                                  (group-by :userid)))
                :reset-fn (fn []
                            (cache/reset! @#'rems.db.user-mappings/by-extidattribute)
                            (cache/reset! @#'rems.db.user-mappings/by-extidvalue)
                            nil)
                :on-evict (fn [_userid]
                            (cache/reset! @#'rems.db.user-mappings/by-extidattribute)
                            (cache/reset! @#'rems.db.user-mappings/by-extidvalue))}))

(def ^:private by-extidattribute
  (cache/basic {:id ::by-extidattribute-cache
                :reload-fn #(group-by :ext-id-attribute (mapcat val (cache/entries! user-mappings-cache)))}))

(def ^:private by-extidvalue
  (cache/basic {:id ::by-extidvalue-cache
                :reload-fn #(group-by :ext-id-value (mapcat val (cache/entries! user-mappings-cache)))}))

(defn get-user-mappings [{:keys [ext-id-attribute ext-id-value userid] :as filters}]
  (let [from-caches [(some->> ext-id-attribute (cache/lookup! by-extidattribute))
                     (some->> ext-id-value (cache/lookup! by-extidvalue))
                     (some->> userid (cache/lookup! user-mappings-cache))]]
    (->> from-caches
         (eduction (mapcat seq)
                   (apply-filters filters)
                   (distinct))
         (into []))))

(defn create-user-mapping! [user-mapping]
  (let [{:keys [userid] :as mapping} (validate-user-mapping user-mapping)]
    (db/create-user-mapping! mapping)
    (cache/evict-and-miss! user-mappings-cache userid)))

(defn delete-user-mapping! [{:keys [userid]}]
  (db/delete-user-mapping! {:userid userid})
  (cache/evict! user-mappings-cache userid))

(defn find-userid
  "Figures out the `userid` of a user reference.

  If a user mapping is found, the corresponding `:userid` is returned.
  Else the string is assumed to be a `userid`."
  [userid-or-ext-id]
  (when-not (str/blank? userid-or-ext-id)
    (let [mappings (get-user-mappings {:ext-id-value userid-or-ext-id})]
      (when (> (count (group-by :userid mappings)) 1)
        (throw (ex-info (str "Multiple mappings found with value " (pr-str userid-or-ext-id))
                        {:key :t.form.validation/invalid-user
                         :value userid-or-ext-id
                         :mappings mappings})))
      (or (some :userid mappings)
          userid-or-ext-id))))

