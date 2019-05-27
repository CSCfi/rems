(ns rems.application-list
  (:require [clojure.string :as str]
            [rems.application-util :as application-util]
            [rems.guide-functions]
            [rems.table :as table]
            [rems.text :refer [localize-state localize-time localized text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- view-button [app]
  [:a.btn.btn-primary
   {:href (str "#/application/" (:application/id app))}
   (text :t.applications/view)])

(defn- format-catalogue-items [app]
  (->> (:application/resources app)
       (map :catalogue-item/title)
       (map localized)
       (str/join ", ")))

(defn- format-description [app]
  [:div {:class "application-description"
         :title (:application/description app)}
   (:application/description app)])

(defn- format-applicant []
  [:div {:class "application-applicant"
         :title :application/applicant}
   :application/applicant])

(defn component
  "A table of applications.

  See `table/component`.

  Binds the column definitions for you and the visible columns should be a subsequence."
  [opts]
  [table/component
   (merge {:column-definitions {:external-id {:value :application/external-id
                                              :header #(text :t.actions/id)}
                                :id {:value :application/id
                                     :header #(text :t.actions/id)}
                                :description {:value format-description
                                              :header #(text :t.actions/description)}
                                :resource {:value format-catalogue-items
                                           :header #(text :t.actions/resource)}
                                :applicant {:value format-applicant
                                            :header #(text :t.actions/applicant)}
                                :state {:value #(localize-state (:application/state %))
                                        :header #(text :t.actions/state)
                                        :class #(if (application-util/form-fields-editable? %) "state text-highlight" "state")}
                                :created {:value #(localize-time (:application/created %))
                                          :sort-value :application/created
                                          :header #(text :t.actions/created)}
                                :submitted {:value #(localize-time (:application/first-submitted %))
                                            :sort-value :application/first-submitted
                                            :header #(text :t.actions/submitted)}
                                :last-activity {:value #(localize-time (:application/last-activity %))
                                                :sort-value :application/last-activity
                                                :header #(text :t.actions/last-activity)}
                                :view {:value view-button
                                       :sortable? false
                                       :filterable? false}}
           :id-function #(str "application-" (:application/id %))
           :class "applications"}
          opts)])

(def ^:private +example-applications+
  [{:application/id 1
    :application/resources [{:catalogue-item/title {:en "Item 5"}}]
    :application/state :application.state/draft
    :application/applicant "alice"
    :application/created "1980-01-02T13:45:00.000Z"
    :application/last-activity "2017-01-01T01:01:01:001Z"}
   {:application/id 2
    :application/resources [{:catalogue-item/title {:en "Item 3"}}]
    :application/state :application.state/submitted
    :application/applicant "bob"
    :application/created "1971-02-03T23:59:00.000Z"
    :application/last-activity "2017-01-01T01:01:01:001Z"}
   {:application/id 3
    :application/resources [{:catalogue-item/title {:en "Item 2"}}
                            {:catalogue-item/title {:en "Item 5"}}]
    :application/state :application.state/approved
    :application/applicant "charlie"
    :application/created "1980-01-01T01:01:00.000Z"
    :application/last-activity "2017-01-01T01:01:01:001Z"}
   {:application/id 4
    :application/resources [{:catalogue-item/title {:en "Item 2"}}]
    :application/state :application.state/rejected
    :application/applicant "david"
    :application/created "1972-12-12T12:12:00.000Z"
    :application/last-activity "2017-01-01T01:01:01:001Z"}
   {:application/id 5
    :application/resources [{:catalogue-item/title {:en "Item 2"}}]
    :application/state :application.state/closed
    :application/applicant "ernie"
    :application/created "1972-12-12T12:12:00.000Z"
    :application/last-activity "2017-01-01T01:01:01:001Z"}])

(defn guide
  []
  [:div
   (component-info component)
   (example "empty list"
            [component {:visible-columns [:id :description :resource :applicant :state :created :last-activity :view]
                        :sorting {:sort-column :id :sort-order :asc}
                        :items []}])
   (example "applications, default order"
            [component {:visible-columns [:id :description :resource :applicant :state :created :last-activity :view]
                        :sorting {:sort-column :id :sort-order :asc}
                        :items +example-applications+}])
   (example "applications, descending date, all columns"
            [component {:visible-columns [:id :description :resource :applicant :state :created :last-activity :view]
                        :sorting {:sort-column :created :sort-order :desc}
                        :items +example-applications+}])
   (example "applications, initially sorted by id descending, then resource descending"
            [component {:visible-columns [:id :description :resource :applicant :state :created :last-activity :view]
                        :sorting {:initial-sort [{:sort-column :id :sort-order :desc}
                                                 {:sort-column :resource :sort-order :desc}]}
                        :items +example-applications+}])])
