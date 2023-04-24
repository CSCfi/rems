(ns rems.experimental.simulator-util
  (:require [clojure.set]
            [com.rpl.specter :refer [ALL select]]
            [rems.browser-test-util :as btu]
            [rems.db.applications]
            [rems.db.catalogue]
            [rems.db.roles]
            [rems.db.test-data-users :refer [+bot-users+]]
            [rems.db.users]
            [rems.service.test-data :as test-data]
            [rems.service.todos]
            [rems.test-browser :as b]
            [rems.text :refer [get-localized-title]]
            [rems.util :refer [rand-nth*]]))

(defmacro with-test-browser [& body]
  `(binding [btu/screenshot (constantly nil)
             btu/screenshot-element (constantly nil)
             btu/check-axe (constantly nil)
             btu/postmortem-handler (constantly nil)]
     (btu/refresh-driver!)
     ~@body))

(def bot-userids (set (vals +bot-users+)))

(defn get-db-roles [user-id]
  (let [roles (rems.db.roles/get-roles user-id)]
    (disj roles :logged-in)))

(defn get-all-users []
  (let [users (rems.db.users/get-users)]
    (->> users
         (map :userid)
         (remove #(contains? bot-userids %))
         (remove #(seq (get-db-roles %)))
         (set))))

(defn get-application-fields [app-id]
  (let [application (rems.db.applications/get-application-internal app-id)]
    (->> application
         (select [:application/forms ALL :form/fields ALL]))))

(defn get-random-application [user-id]
  (let [applications (rems.db.applications/get-my-applications user-id)]
    (rand-nth* applications)))

(defn get-random-todo-application [user-id]
  (let [applications (rems.service.todos/get-todos user-id)]
    (rand-nth* applications)))

(defn get-random-catalogue-item []
  (let [catalogue-item (->> (rems.db.catalogue/get-localized-catalogue-items {:archived false :enabled true})
                            (remove :expired)
                            (rand-nth*))]
    (get-localized-title catalogue-item :en)))

#_(defn fill-human [selector value]
    (apply (btu/wrap-etaoin et/fill-human)
           [selector value {:mistake-prob 0 :pause-max 0.1}]))

(defn fill-field [label value]
  (let [selector {:id (b/get-field-id label)}]
    (btu/fill-human selector value)))

(defn fill-application-fields [app-id]
  (doseq [field (get-application-fields app-id)
          :let [label (get-in field [:field/title :en])]
          :when (:field/visible field) ; ignore conditional fields
          :when (not (:field/optional field))] ; ignore optional fields
    (when-some [value (case (:field/type field)
                        :description (btu/get-seed)
                        (:text :texta) (test-data/random-long-string 5)
                        :email "user@example.com"
                        :phone-number "+358451110000"
                        :ip-address "142.250.74.110"
                        nil)]
      (fill-field label value))))

