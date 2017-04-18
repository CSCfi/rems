(ns rems.db.core
  (:require [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [conman.core :as conman]
            [rems.env :refer [*db*]]))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))

(defn create-test-data! []
  (let [meta (create-form-meta! {:title "metatitle" :user 0})
        form-en (create-form! {:title "entitle" :user 0})
        form-fi (create-form! {:title "fititle" :user 0})
        item-c (create-form-item!
                {:title "C" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-a (create-form-item!
                {:title "A" :type "text" :inputprompt "prompt" :user 0 :value 0})
        item-b (create-form-item!
                {:title "B" :type "text" :inputprompt "prompt" :user 0 :value 0})]
    (link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user 0})
    (link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user 0})
    (link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user 0})
    (link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user 0})

    (create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
    (create-catalogue-item! {:title "ELFA Corpus"
                             :form (:id meta)
                             :resid 1
                             :wfid nil})
    (create-catalogue-item! {:title "B"
                             :form nil
                             :resid nil
                             :wfid nil})))
