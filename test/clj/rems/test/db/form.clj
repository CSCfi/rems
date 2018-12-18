(ns rems.test.db.form
  (:require [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.form :as form]
            [rems.test.db :refer [db-each-fixture db-once-fixture]]))

(use-fixtures :once db-once-fixture)
(use-fixtures :each db-each-fixture)

(deftest text-form-item-maxlength
  (let [uid "test-user"
        form-id (:id (db/create-form! {:organization "org" :title "form with max lengths" :user uid}))
        wf-id (:id (db/create-workflow! {:organization "org" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0}))
        item-id (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))
        text-without-limit (db/create-form-item! {:type "text" :user uid :value 0})
        text-with-limit (db/create-form-item! {:type "text" :user uid :value 0})
        texta-with-limit (db/create-form-item! {:type "texta" :user uid :value 0})
        _ (db/link-form-item! {:form form-id :itemorder 1 :item (:id text-without-limit) :user uid :optional false})
        _ (db/link-form-item! {:form form-id :itemorder 2 :item (:id text-with-limit) :user uid :optional false :maxlength 10})
        _ (db/link-form-item! {:form form-id :itemorder 3 :item (:id texta-with-limit) :user uid :optional false :maxlength 20})
        _ (db/add-user! {:user uid :userattrs nil})
        app-id (applications/create-new-draft uid wf-id)]
    (db/add-application-item! {:application app-id :item item-id})
    (let [form (applications/get-form-for uid app-id)]
      (is (= [nil 10 20] (map :maxlength (:items form)))
          "maxlength is returned for items with maxlength set")

      (let [long-value (apply str (repeat 100 "a"))
            response (form/api-save {:application-id app-id
                                     :catalogue-items {}
                                     :items {(:id text-without-limit) long-value
                                             (:id text-with-limit) long-value
                                             (:id texta-with-limit) long-value}
                                     :licenses {}
                                     :command "submit"
                                     :actor uid})
            form (applications/get-form-for uid app-id)]
        (is (= [[(:id text-with-limit) :t.form.validation/toolong]
                [(:id texta-with-limit) :t.form.validation/toolong]]
               (map (juxt :id :key) (:validation response)))
            "fields with maxlength are reported too long")
        (is (= [long-value long-value long-value]
               (map :value (:items form)))
            "values are saved as they are and not limited")))))
