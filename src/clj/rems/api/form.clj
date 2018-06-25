(ns rems.api.form
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Form
  {:id s/Num
   :title s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool})

(defn- format-form
  [{:keys [id title start endt active?]}]
  {:id id
   :title title
   :start start
   :end endt
   :active active?})

(defn- get-forms [filters]
  (doall
   (for [wf (form/get-forms filters)]
     (format-form wf))))

(def form-api
  (context "/form" []
    :tags ["form"]

    (GET "/" []
      :summary "Get forms"
      :query-params [{active :- (describe s/Bool "filter active or inactive forms") nil}]
      :return [Form]
      (check-user)
      (check-roles :owner)
      (ok (get-forms (when-not (nil? active) {:active? active}))))))
