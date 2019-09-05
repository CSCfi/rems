(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch post! put!]]))

(defn- update-loading [db]
  (let [progress (::loading-progress db)]
    (if (<= 2 progress)
      (dissoc db ::loading-progress ::loading?)
      (assoc db ::loading-progress (inc progress)))))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-item-id]]
   {:db (-> (dissoc db ::form)
            (assoc ::catalogue-item-id catalogue-item-id
                   ::loading-catalogue-item? (not (nil? catalogue-item-id))
                   ::loading? true
                   ::loading-progress 0))
    ::fetch-catalogue-item catalogue-item-id
    ::fetch-workflows nil
    ::fetch-resources nil
    ::fetch-forms nil}))

(rf/reg-sub ::catalogue-item (fn [db _] (::catalogue-item db)))
(rf/reg-sub ::editing? (fn [db _] (not (nil? (::catalogue-item-id db)))))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-workflow-id (fn [db _] (get-in db [::form :workflow-id])))
(rf/reg-event-db ::set-selected-workflow-id (fn [db [_ workflow-id]] (assoc-in db [::form :workflow-id] workflow-id)))

(rf/reg-sub ::selected-resource-id (fn [db _] (get-in db [::form :resource-id])))
(rf/reg-event-db ::set-selected-resource-id (fn [db [_ resource-id]] (assoc-in db [::form :resource-id] resource-id)))

(rf/reg-sub ::selected-form-id (fn [db _] (get-in db [::form :form-id])))
(rf/reg-event-db ::set-selected-form-id (fn [db [_ form-id]] (assoc-in db [::form :form-id] form-id)))

(defn- valid-localization? [localization]
  (not (str/blank? (:title localization))))

(defn- valid-request? [request languages]
  (and (number? (:wfid request))
       (number? (:resid request))
       (number? (:form request))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

(defn- empty-string-to-nil [str]
  (when-not (str/blank? str)
    str))

(defn build-request [form languages]
  (let [request {:wfid (:workflow-id form)
                 :resid (:resource-id form)
                 :form (:form-id form)
                 :localizations (into {}
                                      (for [lang languages]
                                        [lang {:title (get-in form [:title lang])
                                               :infourl (empty-string-to-nil
                                                         (get-in form [:infourl lang]))}]))}]
    (when (valid-request? request languages)
      request)))

(defn- page-title [editing?]
  (if editing?
    (text :t.administration/edit-catalogue-item)
    (text :t.administration/create-catalogue-item)))

(defn- create-catalogue-item! [_ [_ request]]
  (let [description (text :t.administration/create-catalogue-item)]
    (post! "/api/catalogue-items/create"
           {:params (-> request
                        ;; create disabled catalogue items by default
                        (assoc :enabled false))
            :handler (flash-message/default-success-handler
                      description
                      (fn [response]
                        (dispatch! (str "#/administration/catalogue-items/"
                                        (:id response)))))
            :error-handler (flash-message/default-error-handler description)}))
  {})

(defn- edit-catalogue-item! [{:keys [db]} [_ request]]
  (let [id (::catalogue-item-id db)
        description (text :t.administration/edit-catalogue-item)]
    (put! "/api/catalogue-items/edit"
          {:params {:id id
                    :localizations (:localizations request)}
           :handler (flash-message/default-success-handler
                     description
                     (fn [_]
                       (dispatch! (str "#/administration/catalogue-items/" id))))
           :error-handler (flash-message/default-error-handler description)}))
  {})

(rf/reg-event-fx ::create-catalogue-item create-catalogue-item!)
(rf/reg-event-fx ::edit-catalogue-item edit-catalogue-item!)

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


(defn- fetch-catalogue-item [id]
  (fetch (str "/api/catalogue-items/" id)
         {:handler #(rf/dispatch [::fetch-catalogue-item-result %])}))

(rf/reg-fx
 ::fetch-catalogue-item
 (fn [id]
   (when id (fetch-catalogue-item id))))

(rf/reg-event-db
 ::fetch-catalogue-item-result
 (fn [db [_ {:keys [wfid resource-id formid localizations] :as catalogue-item}]]
   (-> db
       (assoc ::form {:workflow-id wfid
                      :resource-id resource-id
                      :form-id formid
                      :title (map-vals :title localizations)
                      :infourl (map-vals :infourl localizations)})
       (dissoc ::loading-catalogue-item?))))

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private workflow-dropdown-id "workflow-dropdown")
(def ^:private resource-dropdown-id "resource-dropdown")
(def ^:private form-dropdown-id "form-dropdown")

(defn- catalogue-item-title-field [language]
  [text-field context {:keys [:title language]
                       :label (str (text :t.create-catalogue-item/title)
                                   " (" (str/upper-case (name language)) ")")
                       :placeholder (text :t.create-catalogue-item/title-placeholder)}])

(defn- catalogue-item-infourl-field [language]
  [text-field context {:keys [:infourl language]
                       ;; no placeholder to make clear that field is optional
                       :label (str (text :t.catalogue/more-info) " URL " ;; TODO localization
                                   " (" (str/upper-case (name language)) ")")}])

(defn- catalogue-item-workflow-field []
  (let [workflows @(rf/subscribe [::workflows])
        editing? @(rf/subscribe [::editing?])
        catalogue-item @(rf/subscribe [::catalogue-item])
        selected-workflow-id @(rf/subscribe [::selected-workflow-id])
        item-selected? #(= (:id %) selected-workflow-id)]
    [:div.form-group
     [:label {:for workflow-dropdown-id} (text :t.create-catalogue-item/workflow-selection)]
     [dropdown/dropdown
      {:id workflow-dropdown-id
       :items workflows
       :item-disabled? (if editing?
                         (comp not item-selected?)
                         (constantly false))
       :item-key :id
       :item-label :title
       :item-selected? item-selected?
       :on-change #(rf/dispatch [::set-selected-workflow-id (:id %)])}]]))

(defn- catalogue-item-resource-field []
  (let [resources @(rf/subscribe [::resources])
        editing? @(rf/subscribe [::editing?])
        selected-resource-id @(rf/subscribe [::selected-resource-id])
        item-selected? #(= (:id %) selected-resource-id)]
    [:div.form-group
     [:label {:for resource-dropdown-id} (text :t.create-catalogue-item/resource-selection)]
     [dropdown/dropdown
      {:id resource-dropdown-id
       :items resources
       :item-disabled? (if editing?
                         (comp not item-selected?)
                         (constantly false))
       :item-key :id
       :item-label :resid
       :item-selected? item-selected?
       :on-change #(rf/dispatch [::set-selected-resource-id (:id %)])}]]))

(defn- catalogue-item-form-field []
  (let [forms @(rf/subscribe [::forms])
        editing? @(rf/subscribe [::editing?])
        selected-form-id @(rf/subscribe [::selected-form-id])
        item-selected? #(= (:form/id %) selected-form-id)]
    [:div.form-group
     [:label {:for form-dropdown-id} (text :t.create-catalogue-item/form-selection)]
     [dropdown/dropdown
      {:id form-dropdown-id
       :items forms
       :item-disabled? (if editing?
                         (comp not item-selected?)
                         (constantly false))
       :item-key :id
       :item-label :form/title
       :item-selected? item-selected?
       :on-change #(rf/dispatch [::set-selected-form-id (:id %)])}]]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/catalogue-items"
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [form languages editing?]
  (let [request (build-request form languages)]
    [:button.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (if editing?
                    (rf/dispatch [::edit-catalogue-item request])
                    (rf/dispatch [::create-catalogue-item request])))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn create-catalogue-item-page []
  (let [languages @(rf/subscribe [:languages])
        editing? @(rf/subscribe [::editing?])
        loading? @(rf/subscribe [::loading?])
        form @(rf/subscribe [::form])]
    [:div
     [administration-navigator-container]
     [document-title (page-title editing?)]
     [flash-message/component]
     [collapsible/component
      {:id "create-catalogue-item"
       :title (page-title editing?)
       :always [:div
                (if loading?
                  [:div#catalogue-item-loader [spinner/big]]
                  [:div#catalogue-item-editor
                   (for [language languages]
                     [:<>
                      ^{:key (str "title-" language)} [catalogue-item-title-field language]
                      ^{:key (str "infourl-" language)} [catalogue-item-infourl-field language]])
                   [catalogue-item-workflow-field]
                   [catalogue-item-resource-field]
                   [catalogue-item-form-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-catalogue-item-button form languages editing?]]])]}]]))
