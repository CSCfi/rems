(ns rems.db.attachments
  (:require [clj-time.core :as time]
            [rems.cache :as cache]
            [rems.common.util :refer [getx index-by]]
            [rems.db.core :as db]
            [schema.core :as s]
            [schema.coerce :as coerce]))

(s/defschema AttachmentDb
  {:attachment/id s/Int
   :attachment/user s/Str
   :attachment/filename s/Str
   :attachment/type s/Str
   :application/id s/Int})

(def ^:private coerce-AttachmentDb
  (coerce/coercer! AttachmentDb coerce/string-coercion-matcher))

(defn- parse-attachment-raw [x]
  (coerce-AttachmentDb {:application/id (:appid x)
                        :attachment/id (:id x)
                        :attachment/user (:userid x)
                        :attachment/filename (:filename x)
                        :attachment/type (:type x)}))

(def attachment-cache
  (cache/basic {:id ::attachment-cache
                :miss-fn (fn [id]
                           (if-let [attachment (db/get-attachment-metadata {:id id})]
                             (parse-attachment-raw attachment)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-attachments-metadata)
                                  (mapv parse-attachment-raw)
                                  (index-by [:attachment/id])))}))

(def ^:private by-application-id
  (cache/basic {:id ::by-application-id-cache
                :depends-on [::attachment-cache]
                :reload-fn (fn []
                             (->> (vals (cache/entries! attachment-cache))
                                  (group-by :application/id)))}))

(defn get-attachment [attachment-id]
  (cache/lookup-or-miss! attachment-cache attachment-id))

(defn get-attachment-data [attachment-id]
  (:data (db/get-attachment {:id attachment-id})))

(defn get-attachments
  "Gets attachments without the data."
  []
  (vals (cache/entries! attachment-cache)))

(defn get-attachments-for-application [application-id]
  (->> (cache/lookup! by-application-id application-id)
       (mapv #(dissoc % :application/id))))

(defn save-attachment!
  [{:keys [data filename content-type user-id application-id]}]
  (let [id (:id (db/save-attachment! {:application application-id
                                      :user user-id
                                      :filename filename
                                      :type content-type
                                      :data data}))]
    (cache/miss! attachment-cache id)
    id))

(defn update-attachment!
  "Updates the attachment, but does not modify the file data! Also does not \"fix the filename\"."
  [attachment]
  (let [id (:attachment/id attachment)]
    (db/update-attachment! {:id id
                            :application (:application/id attachment)
                            :user (:attachment/user attachment)
                            :filename (:attachment/filename attachment)
                            :type (:attachment/type attachment)})
    (cache/miss! attachment-cache id)
    id))

(defn redact-attachment!
  "Updates the attachment by zeroing the file data."
  [attachment-id]
  (db/redact-attachment! {:id attachment-id})
  ;; no need to update metadata cache
  attachment-id)

(defn copy-attachment! [new-application-id attachment-id]
  (let [attachment (get-attachment attachment-id)
        id (:id (db/save-attachment! {:application new-application-id
                                      :user (:attachment/user attachment)
                                      :filename (:attachment/filename attachment)
                                      :type (:attachment/type attachment)
                                      :data (get-attachment-data attachment-id)}))]
    (cache/miss! attachment-cache id)
    id))

(defn delete-attachment! [id]
  (db/delete-attachment! {:id id})
  (cache/evict! attachment-cache id))

(defn delete-application-attachments! [application-id]
  (when-let [attachments (seq (cache/lookup! by-application-id application-id))]
    (run! #(db/delete-attachment! {:id (:attachment/id %)}) attachments)
    (cache/reset! attachment-cache)
    (cache/ensure-initialized! attachment-cache)))

(s/defschema LicenseAttachmentDb
  {:attachment/id s/Int
   :attachment/user s/Str
   :attachment/filename s/Str
   :attachment/type s/Str
   ;; :start s/Any ; XXX: unused database attribute?
   })

(def ^:private coerce-LicenseAttachmentDb
  (coerce/coercer! LicenseAttachmentDb coerce/string-coercion-matcher))

(defn- parse-license-attachment-raw [x]
  (coerce-LicenseAttachmentDb {:attachment/id (:id x)
                               :attachment/user (:userid x)
                               :attachment/filename (:filename x)
                               :attachment/type (:type x)
                               ;; :start (:start x) ; XXX: unused database attribute?
                               }))

;; XXX: license attachments should probably be migrated to attachments?
(def license-attachments-cache
  (cache/basic {:id ::license-attachments-cache
                :miss-fn (fn [id]
                           (if-let [attachment (db/get-license-attachment-metadata {:id id})]
                             (parse-license-attachment-raw attachment)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-license-attachments-metadata)
                                  (mapv parse-license-attachment-raw)
                                  (index-by [:attachment/id])))}))

(defn create-license-attachment! [{:keys [user-id filename content-type data]}]
  (let [id (:id (db/create-license-attachment! {:user user-id
                                                :filename filename
                                                :type content-type
                                                :data data
                                                :start (time/now)}))]
    (cache/miss! license-attachments-cache id)
    id))

(defn remove-license-attachment! [id]
  (db/remove-license-attachment! {:id id})
  (cache/evict! license-attachments-cache id))

(defn get-license-attachment-metadata [id]
  (cache/lookup-or-miss! license-attachments-cache id))

(defn join-license-attachment-data [x]
  (when-let [id (:attachment/id x)]
    (assoc x :attachment/data (:data (db/get-license-attachment {:id id})))))
