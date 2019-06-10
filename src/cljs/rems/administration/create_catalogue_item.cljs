(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :refer [document-title]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch post!]]))

(defn- update-loading [db]
  (let [progress (::loading-progress db)]
    (if (<= 2 progress)
      (dissoc db ::loading-progress ::loading?)
      (assoc db ::loading-progress (inc progress)))))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (-> (dissoc db ::form)
            (assoc ::loading? true
                   ::loading-progress 0))
    ::fetch-workflows nil
    ::fetch-resources nil
    ::fetch-forms nil}))

(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-workflow (fn [db _] (get-in db [::form :workflow])))
(rf/reg-event-db ::set-selected-workflow (fn [db [_ workflow]] (assoc-in db [::form :workflow] workflow)))

(rf/reg-sub ::selected-resource (fn [db _] (get-in db [::form :resource])))
(rf/reg-event-db ::set-selected-resource (fn [db [_ resource]] (assoc-in db [::form :resource] resource)))

(rf/reg-sub ::selected-form (fn [db _] (get-in db [::form :form])))
(rf/reg-event-db ::set-selected-form (fn [db [_ form]] (assoc-in db [::form :form] form)))

(defn- valid-request? [request]
  (and (not (str/blank? (:title request)))
       (number? (:wfid request))
       (number? (:resid request))
       (number? (:form request))))

(defn build-request [form languages]
  (let [request {:title (get (:title form) (first languages))
                 :wfid (get-in form [:workflow :id])
                 :resid (get-in form [:resource :id])
                 :form (get-in form [:form :id])}]
    (when (valid-request? request)
      request)))

(defn- create-catalogue-item! [_ [_ request]]
  (status-modal/common-pending-handler! (text :t.administration/create-catalogue-item))
  (post! "/api/catalogue-items/create" {:params (assoc request :enabled false) ;; create disabled catalogue items by default
                                        :handler (partial status-modal/common-success-handler! #(dispatch! (str "#/administration/catalogue-items/" (:id %))))
                                        :error-handler status-modal/common-error-handler!})
  {})

(rf/reg-event-fx ::create-catalogue-item create-catalogue-item!)


(defn- fetch-workflows []
  (fetch "/api/workflows" {:handler #(rf/dispatch [::fetch-workflows-result %])}))

(rf/reg-fx ::fetch-workflows fetch-workflows)

(rf/reg-event-db
 ::fetch-workflows-result
 (fn [db [_ workflows]]
   (-> (assoc db ::workflows workflows)
       (update-loading))))

(rf/reg-sub ::workflows (fn [db _] (::workflows db)))

(defn- fetch-resources []
  (fetch "/api/resources" {:handler #(rf/dispatch [::fetch-resources-result %])}))

(rf/reg-fx ::fetch-resources fetch-resources)

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> (assoc db ::resources resources)
       (update-loading))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))


(defn- fetch-forms []
  (fetch "/api/forms" {:handler #(rf/dispatch [::fetch-forms-result %])}))

(rf/reg-fx ::fetch-forms fetch-forms)

(rf/reg-event-db
 ::fetch-forms-result
 (fn [db [_ forms]]
   (-> (assoc db ::forms forms)
       (update-loading))))

(rf/reg-sub ::forms (fn [db _] (::forms db)))


;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(defn- catalogue-item-title-field [language]
  [text-field context {:keys [:title language]
                       :label (str (text :t.create-catalogue-item/title)
                                   " (" (name language) ")")
                       :placeholder (text :t.create-catalogue-item/title-placeholder)}])

(defn- catalogue-item-workflow-field []
  (let [workflows @(rf/subscribe [::workflows])
        selected-workflow @(rf/subscribe [::selected-workflow])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/workflow-selection)]
     [autocomplete/component
      {:value (when selected-workflow #{selected-workflow})
       :items workflows
       :value->text #(:title %2)
       :item->key :id
       :item->text :title
       :item->value identity
       :search-fields [:title]
       :add-fn #(rf/dispatch [::set-selected-workflow %])
       :remove-fn #(rf/dispatch [::set-selected-workflow nil])}]]))

(defn- catalogue-item-resource-field []
  (let [resources @(rf/subscribe [::resources])
        selected-resource @(rf/subscribe [::selected-resource])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/resource-selection)]
     [autocomplete/component
      {:value (when selected-resource #{selected-resource})
       :items resources
       :value->text #(:resid %2)
       :item->key :id
       :item->text :resid
       :item->value identity
       :search-fields [:resid]
       :add-fn #(rf/dispatch [::set-selected-resource %])
       :remove-fn #(rf/dispatch [::set-selected-resource nil])}]]))

(defn- catalogue-item-form-field []
  (let [forms @(rf/subscribe [::forms])
        selected-form @(rf/subscribe [::selected-form])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/form-selection)]
     [autocomplete/component
      {:value (when selected-form #{selected-form})
       :items forms
       :value->text #(:title %2)
       :item->key :id
       :item->text :title
       :item->value identity
       :search-fields [:title]
       :add-fn #(rf/dispatch [::set-selected-form %])
       :remove-fn #(rf/dispatch [::set-selected-form nil])}]]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:type :button
    :on-click #(dispatch! "/#/administration/catalogue-items")}
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [form languages on-click]
  (let [request (build-request form languages)]
    [:button.btn.btn-primary
     {:type :button
      :on-click #(on-click request)
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn create-catalogue-item-page []
  (let [languages @(rf/subscribe [:languages])
        loading? @(rf/subscribe [::loading?])
        form @(rf/subscribe [::form])]
    [:div
     [administration-navigator-container]
     [document-title (text :t.administration/create-catalogue-item)]
     [collapsible/component
      {:id "create-catalogue-item"
       :title (text :t.administration/create-catalogue-item)
       :always [:div
                (if loading?
                  [:div#catalogue-item-loader [spinner/big]]
                  [:div#catalogue-item-editor
                   (for [language languages]
                     ^{:key language} [catalogue-item-title-field language])
                   [catalogue-item-workflow-field]
                   [catalogue-item-resource-field]
                   [catalogue-item-form-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-catalogue-item-button form languages #(rf/dispatch [::create-catalogue-item %])]]])]}]]))
