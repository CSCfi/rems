(ns rems.administration.create-workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [radio-button-group text-field]]
            [rems.atoms :as atoms :refer [enrich-user document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch post! put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db
               ::workflow-id workflow-id
               ::loading-workflow? (not (nil? workflow-id))
               ::actors nil
               ::form {:type :dynamic})
    ::fetch-actors nil
    ::fetch-workflow workflow-id}))

(rf/reg-sub ::workflow-id (fn [db _] (::workflow-id db)))
(rf/reg-sub ::editing? (fn [db _] (not (nil? (::workflow-id db)))))
(rf/reg-sub ::loading? (fn [db _] (or (::loading-workflow? db) (nil? (::actors db)))))

;;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))

(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

;;; fetching workflow

(rf/reg-fx
 ::fetch-workflow
 (fn [workflow-id]
   (when workflow-id
     (fetch (str "/api/workflows/" workflow-id)
            {:handler #(rf/dispatch [::fetch-workflow-result %])}))))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (let [new-stuff {:title (:title workflow)
                    :organization (:organization workflow)
                    :handlers (mapv enrich-user (get-in workflow [:workflow :handlers]))}]
     (-> db
         (update ::form merge new-stuff)
         (dissoc ::loading-workflow?)))))

;;; form submit

(defn- valid-create-request? [request]
  (and (case (:type request)
         :auto-approve true
         :dynamic (seq (:handlers request))
         nil false)
       (not (str/blank? (:organization request)))
       (not (str/blank? (:title request)))))

(defn build-create-request [form]
  (let [request {:organization (:organization form)
                 :title (:title form)
                 :type (:type form)}
        request (case (:type form)
                  :auto-approve request
                  :dynamic (assoc request :handlers (map :userid (:handlers form))))]
    (when (valid-create-request? request)
      request)))

(defn- valid-edit-request? [request]
  (and (number? (:id request))
       (seq (:handlers request))
       (not (str/blank? (:title request)))))

(defn build-edit-request [id form]
  (let [request {:id id
                 :title (:title form)
                 :handlers (map :userid (:handlers form))}]
    (when (valid-edit-request? request)
      request)))

(rf/reg-event-fx
 ::create-workflow
 (fn [_ [_ request]]
   (let [description (text :t.administration/create-workflow)]
     (post! "/api/workflows/create"
            {:params request
             :handler (flash-message/default-success-handler description #(dispatch! (str "#/administration/workflows/" (:id %))))
             :error-handler (flash-message/default-error-handler description)}))
   {}))

(rf/reg-event-fx
 ::edit-workflow
 (fn [_ [_ request]]
   (let [description (text :t.administration/edit-workflow)]
     (put! "/api/workflows/edit"
           {:params request
            :handler (flash-message/default-success-handler description #(dispatch! (str "#/administration/workflows/" (:id request))))
            :error-handler (flash-message/default-error-handler description)}))
   {}))

(rf/reg-event-db ::set-handlers (fn [db [_ handlers]] (assoc-in db [::form :handlers] (sort-by :userid handlers))))

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

(def ^:private handlers-dropdown-id "handlers-dropdown")

(defn- workflow-organization-field []
  [text-field context {:keys [:organization]
                       :readonly @(rf/subscribe [::editing?])
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- workflow-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(defn- workflow-type-field []
  [radio-button-group context {:id :workflow-type
                               :keys [:type]
                               :readonly @(rf/subscribe [::editing?])
                               :orientation :horizontal
                               :options [;; TODO: create a new auto-approve workflow in the style of dynamic workflows
                                         #_{:value :auto-approve
                                            :label (text :t.create-workflow/auto-approve-workflow)}
                                         {:value :dynamic
                                          :label (text :t.create-workflow/dynamic-workflow)}]}])

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])
        id @(rf/subscribe [::workflow-id])
        request (if id
                  (build-edit-request id form)
                  (build-create-request form))]
    [:button.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (if id
                    (rf/dispatch [::edit-workflow request])
                    (rf/dispatch [::create-workflow request])))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/workflows"
   (text :t.administration/cancel)])

(defn workflow-type-description [description]
  [:div.alert.alert-info description])

(defn- workflow-handlers-field []
  (let [form @(rf/subscribe [::form])
        all-handlers @(rf/subscribe [::actors])
        selected-handlers (set (map :userid (get-in form [:handlers])))]
    [:div.form-group
     [:label {:for handlers-dropdown-id} (text :t.create-workflow/handlers)]
     [dropdown/dropdown
      {:id handlers-dropdown-id
       :items all-handlers
       :item-key :userid
       :item-label :display
       :item-selected? #(contains? selected-handlers (% :userid))
       :multi? true
       :on-change #(rf/dispatch [::set-handlers %])}]]))

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
        loading? @(rf/subscribe [::loading?])
        editing? @(rf/subscribe [::editing?])
        title (if editing?
                (text :t.administration/edit-workflow)
                (text :t.administration/create-workflow))]
    [:div
     [administration-navigator-container]
     [document-title title]
     [flash-message/component]
     [collapsible/component
      {:id "create-workflow"
       :title title
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
                   [save-workflow-button]]])}]]))
