(ns rems.api.form-items
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.form-item :as form-item]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def FormItem
  {:id s/Num
   :type s/Str
   :value s/Num
   :visibility s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool
   :owneruserid s/Str
   :modifieruserid s/Str})

(defn- format-form-item [{:keys [id type value visibility start endt active? owneruserid modifieruserid]}]
  {:id id
   :type type
   :value value
   :visibility visibility
   :start start
   :end endt
   :active active?
   :owneruserid owneruserid
   :modifieruserid modifieruserid})

(defn- get-form-items [filters]
  (doall
    (for [wf (form-item/get-form-items filters)]
      (format-form-item wf))))

(def form-items-api
  (context "/form-items" []
    :tags ["form-items"]

    (GET "/" []
      :summary "Get form items"
      :query-params [{active :- (describe s/Bool "filter active or inactive form items") nil}]
      :return [FormItem]
      (check-user)
      (check-roles :owner)
      (ok (get-form-items (when-not (nil? active) {:active? active}))))))
