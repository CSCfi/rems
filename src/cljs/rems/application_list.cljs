(ns rems.application-list
  (:require [clojure.string :as str]
            [rems.application-util :refer [editable?]]
            [rems.guide-functions]
            [rems.table :as table]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn view-button [app]
  [:a.btn.btn-primary
   {:href (str "#/application/" (:id app))}
   (text :t.applications/view)])

(defn- get-catalogue-items [app]
  (str/join ", " (map :title (:catalogue-items app))))

(def +all-columns+
  [:id :description :resource :applicant :state :created :last-modified :view])

(def +default-columns+
  [:id :description :resource :applicant :state :created :view])

(defn state-class [item]
  (if (editable? item)
    "state text-highlight"
    "state"))

(def ^:private +columns+
  {:id {:value :id
        :header #(text :t.actions/application)}
   :description {:value :description
                 :header #(text :t.actions/description)}
   :resource {:value get-catalogue-items
              :header #(text :t.actions/resource)}
   :applicant {:value :applicantuserid
               :header #(text :t.actions/applicant)}
   :state {:value #(localize-state (:state %))
           :header #(text :t.actions/state)
           :class state-class}
   :created {:value #(localize-time (:start %))
             :sort-value :start
             :header #(text :t.actions/created)
             :filterable? false}
   :last-modified {:value #(localize-time (:last-modified %))
                   :sort-value :last-modified
                   :header #(text :t.actions/last-modified)
                   :filterable? false}
   :view {:value view-button
          :sortable? false
          :filterable? false}})

(defn component
  "A table of applications.

  See `table/component`.

  Binds the column definitions for you and the visible columns should be a subsequence."
  [opts]
  [table/component
   (merge {:column-definitions +columns+
           :id-function :id
           :class "applications"}
          opts)])

(def ^:private +example-applications+
  [{:id 1 :catalogue-items [{:title "Item 5"}] :state "draft" :applicantuserid "alice"
    :start "1980-01-02T13:45:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}
   {:id 2 :catalogue-items [{:title "Item 3"}] :state "applied" :applicantuserid "bob"
    :start "1971-02-03T23:59:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}
   {:id 3 :catalogue-items [{:title "Item 2"} {:title "Item 5"}] :state "approved" :applicantuserid "charlie"
    :start "1980-01-01T01:01:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}
   {:id 4 :catalogue-items [{:title "Item 2"}] :state "rejected" :applicantuserid "david"
    :start "1972-12-12T12:12:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}
   {:id 5 :catalogue-items [{:title "Item 2"}] :state "closed" :applicantuserid "ernie"
    :start "1972-12-12T12:12:00.000Z" :last-modified "2017-01-01T01:01:01:001Z"}])

(defn guide
  []
  [:div
   (component-info component)
   (example "empty list"
            [component {:visible-columns +default-columns+
                        :sorting {:sort-column :id :sort-order :asc}
                        :items []}])
   (example "applications, default order"
            [component {:visible-columns +default-columns+
                        :sorting {:sort-column :id :sort-order :asc}
                        :items +example-applications+}])
   (example "applications, descending date, all columns"
            [component {:visible-columns +all-columns+
                        :sorting {:sort-column :created :sort-order :desc}
                        :items +example-applications+}])
   (example "applications, initially sorted by id descending, then resource descending"
            [component {:visible-columns +all-columns+
                        :sorting {:initial-sort [{:sort-column :id :sort-order :desc}
                                                 {:sort-column :resource :sort-order :desc}]}
                        :items +example-applications+}])])
