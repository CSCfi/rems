(ns rems.db.user-mappings
  (:require [clojure.string :as str]
            [rems.common.util :refer [apply-filters conj-vec]]
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

(defn- load-user-mappings []
  (->> (db/get-user-mappings {})
       (mapv format-user-mapping)
       (mapv validate-user-mapping)
       (group-by :ext-id-value)))

;; NB: user mappings are always cached
;; XXX: consider if this should rather be mount state eventually?
(def ^:private user-mappings-by-value (atom nil))

(defn- ensure-cached! []
  (when (nil? @user-mappings-by-value)
    (reset! user-mappings-by-value (load-user-mappings))))

;; TODO: external API or process to reset cache if needed (db updated)
(defn reset-cache! []
  (reset! user-mappings-by-value nil))

(defn get-user-mappings [params]
  (ensure-cached!)
  (->> (if (:ext-id-value params) ; can use index?
         (get @user-mappings-by-value (:ext-id-value params))
         (mapcat val @user-mappings-by-value))
       (apply-filters (dissoc params :ext-id-value))
       not-empty))

(defn create-user-mapping! [user-mapping]
  (ensure-cached!)
  (let [mapping (-> user-mapping
                    validate-user-mapping)]
    (db/create-user-mapping! mapping)
    (swap! user-mappings-by-value
           (fn [mappings]
             (update mappings (:ext-id-value mapping) (comp vec distinct conj-vec) mapping)))))

(defn delete-user-mapping! [userid]
  (ensure-cached!)
  (db/delete-user-mapping! {:userid userid})
  (swap! user-mappings-by-value
         (fn [mappings]
           (->> mappings
                (mapcat val)
                (remove #(= userid (:userid %)))
                (group-by :ext-id-value)))))

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

