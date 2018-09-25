(ns rems.api.forms
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Form
  {:id s/Num
   :organization s/Str
   :title s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool})

(defn- format-form
  [{:keys [id organization title start endt active?]}]
  {:id id
   :organization organization
   :title title
   :start start
   :end endt
   :active active?})

(defn- get-forms [filters]
  (doall
   (for [wf (form/get-forms filters)]
     (format-form wf))))

(def CreateFormCommand
  {:organization s/Str
   :title s/Str
   :items [{:title {s/Keyword s/Str}
            :optional s/Bool
            :type (s/enum "text" "texta" "date")
            :input-prompt (s/maybe {s/Keyword s/Str})}]})

(defn- create-form-item [form item-index {:keys [title optional type input-prompt]}]
  (let [item (db/create-form-item! {:type type
                                    :optional optional
                                    :user (get-user-id)
                                    :value 0})]
    (db/link-form-item! {:form (:id form)
                         :itemorder item-index
                         :optional optional
                         :item (:id item)
                         :user (get-user-id)})
    (doseq [lang (keys title)]
      (db/localize-form-item! {:item (:id item)
                               :langcode (name lang)
                               :title (get title lang)
                               :inputprompt (get input-prompt lang)}))))

(defn- create-form [{:keys [organization title items]}]
  (let [form (db/create-form! {:organization organization
                               :title title
                               :user (get-user-id)})]
    (doseq [[index item] (map-indexed vector items)]
      (create-form-item form index item))
    {:id (:id form)}))

(def forms-api
  (context "/forms" []
    :tags ["forms"]

    (GET "/" []
      :summary "Get forms"
      :query-params [{active :- (describe s/Bool "filter active or inactive forms") nil}]
      :return [Form]
      (check-user)
      (check-roles :owner)
      (ok (get-forms (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create form"
      :body [command CreateFormCommand]
      (check-user)
      (check-roles :owner)
      (ok (create-form command)))))
