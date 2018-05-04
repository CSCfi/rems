(ns rems.api.form
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Form
  {:id s/Num
   :title s/Str})

(def form-api
  (context "/form" []
    :tags ["form"]

    (GET "/" []
      :summary "Get forms"
      :return [Form]
      (check-user)
      (ok (db/get-forms)))))
