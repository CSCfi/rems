(ns rems.application-list
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-util :as application-util]
            [rems.atoms :as atoms]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-state localize-time localized text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- view-button [app]
  [atoms/link {:class "btn btn-primary"
               :aria-label (str (text :t.applications/view) ": " (:application/description app))}
   (str "#/application/" (:application/id app))
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

(defn- format-applicant [applicant]
  [:div {:class "application-applicant"
         :title applicant}
   applicant])

(rf/reg-sub
 ::table-rows
 (fn [[_ apps-sub] _]
   [(rf/subscribe [apps-sub])
    ;; This subscription calls the localization functions in rems.text
    ;; and re-frame must be made aware of the language dependency.
    (rf/subscribe [:language])])
 (fn [[apps _language] _]
   (map (fn [app]
          {:key (:application/id app)
           :id {:value (:application/id app)}
           :external-id {:value (:application/external-id app)}
           :description {:value (:application/description app)
                         :td [:td.description (format-description app)]}
           :resource {:value (format-catalogue-items app)}
           :applicant
           (let [applicant (application-util/get-applicant-name app)]
             {:value applicant
              :td [:td.applicant (format-applicant applicant)]})
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

(defn list [{:keys [id applications visible-columns default-sort-column default-sort-order]
             :or {visible-columns (constantly true)}}]
  (let [all-columns [{:key :id
                      :title (text :t.applications/id)}
                     {:key :external-id
                      :title (text :t.applications/id)}
                     {:key :description
                      :title (text :t.applications/description)}
                     {:key :resource
                      :title (text :t.applications/resource)}
                     {:key :applicant
                      :title (text :t.applications/applicant)}
                     {:key :state
                      :title (text :t.applications/state)}
                     {:key :created
                      :title (text :t.applications/created)}
                     {:key :submitted
                      :title (text :t.applications/submitted)}
                     {:key :last-activity
                      :title (text :t.applications/last-activity)}
                     {:key :view
                      :sortable? false
                      :filterable? false}]
        application-table {:id id
                           :columns (filter #(visible-columns (:key %)) all-columns)
                           :rows [::table-rows applications]
                           :default-sort-column default-sort-column
                           :default-sort-order default-sort-order}]
    [table/table application-table]))

(defn- application-list-defaults []
  (let [config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    {:visible-columns #{id-column :description :resource :applicant :state :created :submitted :last-activity :view}
     :default-sort-column :created
     :default-sort-order :desc}))

(defn component
  "An application list which shows a spinner on initial page load (meant to be
  used with rems.search/reg-fetcher) and a message if there are no applications."
  [{:keys [applications empty-message hidden-columns] :as opts}]
  (cond
    (not @(rf/subscribe [applications :initialized?]))
    [spinner/big]

    (empty? @(rf/subscribe [applications]))
    [:div.applications.alert.alert-success (text (or empty-message :t.applications/empty))]

    :else
    [list (-> (application-list-defaults)
              (update :visible-columns set/difference (set hidden-columns))
              (assoc :id applications)
              (merge opts))]))

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
       :application/applicant-attributes {:eppn "alice"
                                          :commonName "Alice Applicant"}
       :application/created "1980-01-02T13:45:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 2
       :application/resources [{:catalogue-item/title {:en "Item 3"}}]
       :application/state :application.state/submitted
       :application/applicant "bob"
       :application/applicant-attributes {:eppn "bob"
                                          :commonName "Bob Tester"}
       :application/created "1971-02-03T23:59:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 3
       :application/resources [{:catalogue-item/title {:en "Item 2"}}
                               {:catalogue-item/title {:en "Item 5"}}]
       :application/state :application.state/approved
       :application/applicant "charlie"
       :application/applicant-attributes {:eppn "charlie"
                                          :commonName "Charlie Tester"}
       :application/created "1980-01-01T01:01:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 4
       :application/resources [{:catalogue-item/title {:en "Item 2"}}]
       :application/state :application.state/rejected
       :application/applicant "david"
       :application/applicant-attributes {:eppn "david"
                                          :commonName "David Newuser"}
       :application/created "1972-12-12T12:12:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 5
       :application/resources [{:catalogue-item/title {:en "Item 2"}}]
       :application/state :application.state/closed
       :application/applicant "ernie"
       :application/applicant-attributes {:eppn "ernie"
                                          :commonName "Ernie Tester"}
       :application/created "1972-12-12T12:12:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}]))

  [:div
   (component-info list)
   (example "empty list"
            [list {:id ::example1
                   :applications ::no-applications
                   :visible-columns #{:id :description :resource :applicant :state :created :last-activity :view}}])
   (example "applications, default order"
            [list {:id ::example2
                   :applications ::example-applications
                   :visible-columns #{:id :description :resource :applicant :state :created :last-activity :view}}])
   (example "applications, descending date, all columns"
            [list {:id ::example3
                   :applications ::example-applications
                   :default-sort-column :created
                   :default-sort-order :desc}])])
