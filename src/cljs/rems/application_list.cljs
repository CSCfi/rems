(ns rems.application-list
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-util :as application-util]
            [rems.guide-functions]
            [rems.table :as table]
            [rems.table2 :as table2]
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
                                              :sort-value :application/description
                                              :header #(text :t.actions/description)}
                                :resource {:value format-catalogue-items
                                           :header #(text :t.actions/resource)}
                                :applicant {:value :application/applicant
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

(rf/reg-sub
 ::table-rows
 (fn [[_ apps-sub] _]
   [(rf/subscribe [apps-sub])])
 (fn [[apps] _]
   (map (fn [app]
          {:key (:application/id app)
           :external-id (let [value (:application/external-id app)]
                          {:td [:td.external-id value]
                           :sort-value value
                           :filter-value (str/lower-case value)})
           :id (let [value (:application/id app)]
                 {:td [:td.id value]
                  :sort-value value
                  :filter-value (str/lower-case (str value))})
           :description (let [value (:application/description app)]
                          {:td [:td.description (format-description app)]
                           :sort-value value
                           :filter-value (str/lower-case value)})
           :resource (let [value (format-catalogue-items app)]
                       {:td [:td.resource value]
                        :sort-value value
                        :filter-value (str/lower-case value)})
           :applicant (let [value (:application/applicant app)]
                        {:td [:td.applicant value]
                         :sort-value value
                         :filter-value (str/lower-case value)})
           :state (let [value (localize-state (:application/state app))]
                    {:td [:td.state
                          {:class (when (application-util/form-fields-editable? app)
                                    "text-highlight")}
                          value]
                     :sort-value value
                     :filter-value (str/lower-case value)})
           :created (let [value (:application/created app)
                          display-value (localize-time value)]
                      {:td [:td.created display-value]
                       :sort-value value
                       :filter-value (str/lower-case display-value)})
           :submitted (let [value (:application/first-submitted app)
                            display-value (localize-time value)]
                        {:td [:td.submitted display-value]
                         :sort-value value
                         :filter-value (str/lower-case (str display-value))})
           :last-activity (let [value (:application/last-activity app)
                                display-value (localize-time value)]
                            {:td [:td.last-activity display-value]
                             :sort-value value
                             :filter-value (str/lower-case display-value)})
           :view {:td [:td.view [view-button app]]}})
        apps)))

(defn component2 [{:keys [id applications visible-columns default-sort-column default-sort-order]}]
  (let [all-columns [{:key :external-id
                      :title (text :t.actions/id)
                      :sortable? true
                      :filterable? true}
                     {:key :id
                      :title (text :t.actions/id)
                      :sortable? true
                      :filterable? true}
                     {:key :description
                      :title (text :t.actions/description)
                      :sortable? true
                      :filterable? true}
                     {:key :resource
                      :title (text :t.actions/resource)
                      :sortable? true
                      :filterable? true}
                     {:key :applicant
                      :title (text :t.actions/applicant)
                      :sortable? true
                      :filterable? true}
                     {:key :state
                      :title (text :t.actions/state)
                      :sortable? true
                      :filterable? true}
                     {:key :created
                      :title (text :t.actions/created)
                      :sortable? true
                      :filterable? true}
                     {:key :submitted
                      :title (text :t.actions/submitted)
                      :sortable? true
                      :filterable? true}
                     {:key :last-activity
                      :title (text :t.actions/last-activity)
                      :sortable? true
                      :filterable? true}
                     {:key :view
                      :title ""
                      :sortable? false
                      :filterable? false}]
        visible-columns (or visible-columns (constantly true))
        application-table {:id id
                           :columns (filter #(visible-columns (:key %)) all-columns)
                           :rows [::table-rows applications]
                           :default-sort-column default-sort-column
                           :default-sort-order default-sort-order}]
    [:div
     [table2/search application-table]
     [table2/table application-table]]))

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
