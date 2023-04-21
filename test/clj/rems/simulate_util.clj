(ns rems.simulate-util
  (:require [clojure.core.memoize]
            [clojure.set]
            [com.rpl.specter :refer [ALL select]]
            [etaoin.api :as et]
            [rems.browser-test-util :as btu]
            [rems.common.util :refer [parse-int]]
            [rems.db.applications]
            [rems.db.catalogue]
            [rems.db.roles]
            [rems.db.test-data-users :refer [+bot-users+]]
            [rems.db.users]
            [rems.service.test-data :as test-data]
            [rems.service.todos]
            [rems.test-browser :as test-browser]
            [rems.text :refer [get-localized-title]]
            [rems.util :refer [rand-nth*]]))

(defmacro with-test-browser [& body]
  `(binding [btu/screenshot (constantly nil)
             btu/screenshot-element (constantly nil)
             btu/gather-axe-results (constantly nil)
             btu/check-axe (constantly nil)
             btu/postmortem-handler (constantly nil)]
     (btu/refresh-driver!)
     ~@body))

(def bot-userids (set (vals +bot-users+)))

(defn get-all-users []
  (->> (rems.db.users/get-users)
       (map :userid)
       (remove (partial contains? bot-userids))
       (remove #(-> (rems.db.roles/get-roles %)
                    (disj :logged-in)
                    (seq)))
       (set)))

(def get-available-users
  (clojure.core.memoize/ttl get-all-users :ttl/threshold 10000))

(defn get-application-fields [app-id]
  (->> app-id
       (parse-int)
       (rems.db.applications/get-application-internal)
       (select [:application/forms ALL :form/fields ALL])))

(defn get-random-application [user-id & [state]]
  (->> user-id
       (rems.db.applications/get-my-applications)
       (filter (if-not state
                 (constantly true)
                 (comp #{state} :application/state)))
       (rand-nth*)))

(defn get-random-todo-application [user-id]
  (-> user-id
      (rems.service.todos/get-todos)
      (rand-nth*)))

(defn get-random-catalogue-item []
  (let [cat-items (rems.db.catalogue/get-localized-catalogue-items {:archived false
                                                                    :enabled true})]
    (-> (remove :expired cat-items)
        (rand-nth*)
        (get-localized-title :en))))

(defn fill-human [selector value]
  (apply (btu/wrap-etaoin et/fill-human)
         [selector value {:mistake-prob 0 :pause-max 0.1}]))

(defn fill [field]
  (when-some [value (case (:field/type field)
                      :description (btu/get-seed)
                      (:text :texta) (test-data/random-long-string 5)
                      :email "user@example.com"
                      :phone-number "+358451110000"
                      :ip-address "142.250.74.110"
                      nil)]
    (let [label (get-in field [:field/title :en])
          selector {:id (test-browser/get-field-id label)}]
      (fill-human selector value))))

