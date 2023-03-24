(ns rems.application-list
  (:refer-clojure :exclude [list])
  (:require [cljs-time.core :as time]
            [clojure.set :as set]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.common.application-util :as application-util]
            [rems.guide-util :refer [component-info example]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-state localize-todo localize-time localized text text-format]]))

(defn- format-catalogue-items [app]
  (->> (:application/resources app)
       (map :catalogue-item/title)
       (map localized)
       (str/join ", ")))

(defn format-application-id [config application]
  (let [id-column (get config :application-id-column :id)]
    (case id-column
      (:external-id :generated-and-assigned-external-id) (:application/external-id application)
      :id (:application/id application)
      (:application/id application))))

(defn- view-button [app]
  (let [config @(rf/subscribe [:rems.config/config])
        id (format-application-id config app)]
    [atoms/link
     {:class "btn btn-primary"
      :aria-label (if (str/blank? (:application/description app))
                    (text-format :t.applications/view-application-without-description
                                 id (format-catalogue-items app))
                    (text-format :t.applications/view-application-with-description
                                 id (:application/description app)))}
     (str "/application/" (:application/id app))
     (text :t.applications/view)]))

(defn- format-description [app]
  [:div {:class "application-description"
         :title (:application/description app)}
   (:application/description app)])

(defn- format-applicant [applicant]
  [:div {:class "application-applicant"
         :title applicant}
   applicant])

(defn- current-user-needs-to-do-something? [app]
  (or (contains? #{:waiting-for-your-decision
                   :waiting-for-your-review}
                 (:application/todo app))
      (and (contains? (:application/roles app) :handler)
           (contains? #{:new-application
                        :no-pending-requests
                        :resubmitted-application}
                      (:application/todo app)))))

;; could be in some util namespace, but only used here for now
(defn- application-overdue? [application]
  (when-let [dl (:application/deadline application)]
    (time/after? (time/now) dl)))

(defn- application-almost-overdue? [application]
  (when-let [dl (:application/deadline application)]
    (let [start (time/date-time (:application/first-submitted application))
          seconds (time/seconds (* 0.75 (time/in-seconds (time/interval start dl))))
          threshold (time/plus start seconds)]
      (time/after? (time/now) threshold))))


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
           :external-id {:value (:application/external-id app)
                         :sort-value (application-util/parse-sortable-external-id (:application/external-id app))}
           :generated-and-assigned-external-id {:display-value [:<>
                                                                (when (:application/assigned-external-id app)
                                                                  [:<>
                                                                   [:span.text-nowrap (:application/assigned-external-id app)]
                                                                   [:br]])
                                                                [:span.text-nowrap (:application/generated-external-id app)]]
                                                :sort-value [(application-util/parse-sortable-external-id (:application/assigned-external-id app))
                                                             (:application/generated-external-id app)]}
           :description {:value (:application/description app)
                         :display-value (format-description app)}
           :resource {:value (format-catalogue-items app)}
           :applicant (let [applicant (application-util/get-applicant-name app)]
                        {:value applicant
                         :display-value (format-applicant applicant)})
           :handlers (let [handlers (->> (get-in app [:application/workflow :workflow.dynamic/handlers])
                                         (filter :handler/active?)
                                         (map application-util/get-member-name)
                                         (sort)
                                         (str/join ", "))]
                       {:value handlers})
           :state (let [value (localize-state (:application/state app))]
                    {:value value
                     :td [:td.state
                          {:class (when (application-util/form-fields-editable? app)
                                    "text-highlight")}
                          value]})
           :todo (let [value (localize-todo (:application/todo app))]
                   {:value value
                    :td [:td.todo
                         {:class (when (current-user-needs-to-do-something? app)
                                   "text-highlight")}
                         value]})
           :created (let [value (:application/created app)]
                      {:value value
                       :display-value (localize-time value)})
           :submitted (let [value (:application/first-submitted app)]
                        {:value value
                         :td [:td.submitted
                              {:class (cond
                                        (application-overdue? app) "alert-danger"
                                        (application-almost-overdue? app) "alert-warning")}
                              (localize-time value)]})
           :last-activity (let [value (:application/last-activity app)]
                            {:value value
                             :display-value (localize-time value)})
           :view {:display-value [:div.commands.justify-content-end [view-button app]]}})
        apps)))


(defn list [{:keys [id applications visible-columns default-sort-column default-sort-order]
             :or {visible-columns (constantly true)}}]
  (let [all-columns [{:key :id
                      :title (text :t.applications/id)}
                     {:key :external-id
                      :title (text :t.applications/id)}
                     {:key :generated-and-assigned-external-id
                      :title (text :t.applications/id)}
                     {:key :description
                      :title (text :t.applications/description)}
                     {:key :resource
                      :title (text :t.applications/resource)}
                     {:key :applicant
                      :title (text :t.applications/applicant)}
                     {:key :handlers
                      :title (text :t.applications/handlers)}
                     {:key :state
                      :title (text :t.applications/state)}
                     {:key :todo
                      :title (text :t.applications/todo)}
                     {:key :created
                      :title (text :t.applications/created)}
                     {:key :submitted
                      :title (text :t.applications/submitted)}
                     {:key :last-activity
                      :title (text :t.applications/last-activity)}
                     {:key :view
                      :sortable? false
                      :filterable? false
                      :aria-label (text :t.actions/commands)}]
        application-table {:id id
                           :columns (filter #(visible-columns (:key %)) all-columns)
                           :rows [::table-rows applications]
                           :default-sort-column default-sort-column
                           :default-sort-order default-sort-order}]
    [table/table application-table]))

(defn- application-list-defaults []
  (let [config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    {:visible-columns (set/difference #{id-column :description :resource :applicant :handlers :state :todo :created :submitted :last-activity :view}
                                      (set (get config :application-list-hidden-columns)))
     :default-sort-column :created
     :default-sort-order :desc}))

(defn component
  "An application list which shows a spinner on initial page load (meant to be
  used with rems.fetcher/reg-fetcher) and a message if there are no applications."
  [{:keys [applications hidden-columns] :as opts}]
  (cond
    (not @(rf/subscribe [applications :initialized?]))
    [spinner/big]

    @(rf/subscribe [applications :error])
    [:div.applications.alert.alert-danger @(rf/subscribe [applications :error])]

    (empty? @(rf/subscribe [applications]))
    [:div.applications.alert.alert-secondary.mt-3 (text :t.applications/empty)]

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
       :application/applicant {:userid "alice"
                               :name "Alice Applicant"}
       :application/created "1980-01-02T13:45:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 2
       :application/resources [{:catalogue-item/title {:en "Item 3"}}]
       :application/state :application.state/submitted
       :application/applicant {:userid "bob"
                               :name "Bob Tester"}
       :application/created "1971-02-03T23:59:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"
       :application/first-submitted (time/date-time 1971 03 04)
       :application/deadline (time/date-time 1972 01 01)} ;; already expired
      {:application/id 3
       :application/resources [{:catalogue-item/title {:en "Item 1"}}]
       :application/state :application.state/submitted
       :application/applicant {:userid "bob"
                               :name "Bob Tester"}
       :application/created "1971-02-03T23:59:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"
       :application/first-submitted (time/date-time 1971 03 04)
       :application/deadline (time/date-time 2022)} ;; should be "almost expired"
      {:application/id 4
       :application/resources [{:catalogue-item/title {:en "Item 2"}}
                               {:catalogue-item/title {:en "Item 5"}}]
       :application/state :application.state/approved
       :application/applicant {:userid "charlie"
                               :name "Charlie Tester"}
       :application/created "1980-01-01T01:01:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 5
       :application/resources [{:catalogue-item/title {:en "Item 2"}}]
       :application/state :application.state/rejected
       :application/applicant {:userid "david"
                               :name "David Newuser"}
       :application/created "1972-12-12T12:12:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}
      {:application/id 6
       :application/resources [{:catalogue-item/title {:en "Item 2"}}]
       :application/state :application.state/closed
       :application/applicant {:userid "ernie"
                               :name "Ernie Tester"}
       :application/created "1972-12-12T12:12:00.000Z"
       :application/last-activity "2017-01-01T01:01:01:001Z"}]))

  [:div
   (component-info list)
   (example "empty list"
            [list {:id ::example1
                   :applications ::no-applications}])
   (example "applications, default order, limited columns"
            [list {:id ::example2
                   :applications ::example-applications
                   :visible-columns #{:id :description :resource :applicant :state :created :last-activity :view}}])
   (example "applications, sort descending date, all columns"
            [list {:id ::example3
                   :applications ::example-applications
                   :default-sort-column :created
                   :default-sort-order :desc}])])
