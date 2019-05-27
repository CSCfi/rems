(ns rems.application-list
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-util :as application-util]
            [rems.guide-functions]
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

(rf/reg-sub
 ::table-rows
 (fn [[_ apps-sub] _]
   [(rf/subscribe [apps-sub])])
 (fn [[apps] _]
   (map (fn [app]
          {:key (:application/id app)
           :id {:value (:application/id app)}
           :external-id {:value (:application/external-id app)}
           :description {:value (:application/description app)
                         :td [:td.description (format-description app)]}
           :resource {:value (format-catalogue-items app)}
           :applicant {:value (:application/applicant app)}
           :state (let [value (localize-state (:application/state app))]
                    {:value value
                     :td [:td.state
                          {:class (when (application-util/form-fields-editable? app)
                                    "text-highlight")}
                          value]})
           :created (let [value (:application/created app)]
                      {:value value
                       :display-value (localize-time value)})
           :submitted (let [value (:application/first-submitted app)]
                        {:value value
                         :display-value (localize-time value)})
           :last-activity (let [value (:application/last-activity app)]
                            {:value value
                             :display-value (localize-time value)})
           :view {:td [:td.view [view-button app]]}})
        apps)))

(defn component [{:keys [id applications visible-columns default-sort-column default-sort-order filterable?]
                  :or {visible-columns (constantly true) filterable? true}}]
  (let [all-columns [{:key :id
                      :title (text :t.actions/id)}
                     {:key :external-id
                      :title (text :t.actions/id)}
                     {:key :description
                      :title (text :t.actions/description)}
                     {:key :resource
                      :title (text :t.actions/resource)}
                     {:key :applicant
                      :title (text :t.actions/applicant)}
                     {:key :state
                      :title (text :t.actions/state)}
                     {:key :created
                      :title (text :t.actions/created)}
                     {:key :submitted
                      :title (text :t.actions/submitted)}
                     {:key :last-activity
                      :title (text :t.actions/last-activity)}
                     {:key :view
                      :sortable? false
                      :filterable? false}]
        application-table {:id id
                           :columns (filter #(visible-columns (:key %)) all-columns)
                           :rows [::table-rows applications]
                           :default-sort-column default-sort-column
                           :default-sort-order default-sort-order}]
    [:div
     (when filterable?
       [table2/search application-table])
     [table2/table application-table]]))

(defn guide []
  (rf/reg-sub
   ::no-applications
   (fn [_ _]
     []))

  (rf/reg-sub
   ::example-applications
   (fn [_ _]
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
       :application/last-activity "2017-01-01T01:01:01:001Z"}]))

  [:div
   (component-info component)
   (example "empty list"
            [component {:id ::example1
                        :applications ::no-applications
                        :visible-columns #{:id :description :resource :applicant :state :created :last-activity :view}}])
   (example "applications, default order"
            [component {:id ::example2
                        :applications ::example-applications
                        :visible-columns #{:id :description :resource :applicant :state :created :last-activity :view}}])
   (example "applications, descending date, all columns"
            [component {:id ::example3
                        :applications ::example-applications
                        :default-sort-column :created
                        :default-sort-order :desc}])])
