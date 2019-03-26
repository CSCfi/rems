(ns rems.administration.create-workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [radio-button-group text-field]]
            [rems.status-modal :as status-modal]
            [rems.atoms :refer [enrich-user]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db
               ::form {:type :auto-approve}
               ::loading? true)
    ::fetch-actors nil}))

(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))

(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))


;;; form submit

(defn- valid-round? [round]
  (and (not (nil? (:type round)))
       (not (empty? (:actors round)))))

(defn- valid-request? [request]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:title request)))
       (case (:type request)
         :auto-approve true
         :dynamic (not (empty? (:handlers request)))
         nil false)))

(defn build-request [form]
  (let [request {:organization (:organization form)
                 :title (:title form)
                 :type (:type form)}
        request (case (:type form)
                  :auto-approve request
                  :dynamic (assoc request :handlers (map :userid (:handlers form))))]
    (when (valid-request? request)
      request)))

(rf/reg-event-fx
 ::create-workflow
 (fn [_ [_ request]]
   (status-modal/common-pending-handler! (text :t.administration/create-workflow))
   (post! "/api/workflows/create" {:params request
                                   :handler (partial status-modal/common-success-handler! #(dispatch! (str "#/administration/workflows/" (:id %))))
                                   :error-handler status-modal/common-error-handler!})
   {}))

(defn- remove-actor [actors actor]
  (filter #(not= (:userid %)
                 (:userid actor))
          actors))

(defn- add-actor [actors actor]
  (-> actors
      (remove-actor actor) ; avoid duplicates
      (conj actor)))

(rf/reg-event-db ::remove-handler (fn [db [_ handler]] (update-in db [::form :handlers] remove-actor handler)))
(rf/reg-event-db ::add-handler (fn [db [_ handler]] (update-in db [::form :handlers] add-actor handler)))


(defn- fetch-actors []
  (fetch "/api/workflows/actors" {:handler #(rf/dispatch [::fetch-actors-result %])}))

(rf/reg-fx ::fetch-actors fetch-actors)

(rf/reg-event-db
 ::fetch-actors-result
 (fn [db [_ actors]]
   (-> db
       (assoc ::actors (map enrich-user actors))
       (dissoc ::loading?))))

(rf/reg-sub ::actors (fn [db _] (::actors db)))


;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(defn- workflow-organization-field []
  [text-field context {:keys [:organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- workflow-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(defn- workflow-type-field []
  [radio-button-group context {:id :workflow-type
                               :keys [:type]
                               :orientation :horizontal
                               :options (concat [{:value :auto-approve
                                                  :label (text :t.create-workflow/auto-approve-workflow)}]
                                                [{:value :dynamic
                                                  :label (text :t.create-workflow/dynamic-workflow)}])}])

(defn- save-workflow-button [on-click]
  (let [form @(rf/subscribe [::form])
        request (build-request form)]
    [:button.btn.btn-primary
     {:on-click #(on-click request)
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/workflows")}
   (text :t.administration/cancel)])

(defn workflow-type-description [description]
  [:div.alert.alert-info description])

(defn- workflow-handlers-field []
  (let [form @(rf/subscribe [::form])
        all-handlers @(rf/subscribe [::actors])
        selected-handlers (get-in form [:handlers])]
    [:div.form-group
     [:label (text :t.create-workflow/handlers)]
     [autocomplete/component
      {:value (sort-by :userid selected-handlers)
       :items all-handlers
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:display :userid]
       :add-fn #(rf/dispatch [::add-handler %])
       :remove-fn #(rf/dispatch [::remove-handler %])}]]))

(defn dynamic-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/dynamic-workflow-description)]
   [workflow-handlers-field]])

(defn auto-approve-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/auto-approve-workflow-description)]])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])
        workflow-type (:type form)
        loading? @(rf/subscribe [::loading?])]
    [:div
     [administration-navigator-container]
     [:h2 (text :t.administration/create-workflow)]
     [collapsible/component
      {:id "create-workflow"
       :title (text :t.administration/create-workflow)
       :always (if loading?
                 [:div#workflow-loader [spinner/big]]
                 [:div#workflow-editor
                  [workflow-organization-field]
                  [workflow-title-field]
                  [workflow-type-field]

                  (case workflow-type
                    :auto-approve [auto-approve-workflow-form]
                    :dynamic [dynamic-workflow-form])

                  [:div.col.commands
                   [cancel-button]
                   [save-workflow-button #(rf/dispatch [::create-workflow %])]]])}]]))
