(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [navigate! fetch post! put! trim-when-string]]))

(defn- update-loading [db]
  (let [progress (::loading-progress db)
        catalogue-item-id (::catalogue-item-id db)]
    (cond
      (<= progress 1)
      {:db (assoc db ::loading-progress (inc progress))}

      (nil? catalogue-item-id)
      {:db (dissoc db ::loading-progress ::loading?)}

      :else
      {:db (-> db
               (dissoc ::loading-progress)
               (assoc ::loading-catalogue-item? true))
       ::fetch-catalogue-item catalogue-item-id})))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-item-id]]
   (let [roles (get-in db [:identity :roles])
         organization (get-in db [:identity :user :organization])]
     {:db (assoc db
                 ::form (when (roles/disallow-setting-organization? roles)
                          {:organization organization})
                 ::catalogue-item-id catalogue-item-id
                 ::loading? true
                 ::loading-progress 0)
      ::fetch-workflows nil
      ::fetch-resources nil
      ::fetch-forms nil})))

(rf/reg-sub ::catalogue-item (fn [db _] (::catalogue-item db)))
(rf/reg-sub ::editing? (fn [db _] (not (nil? (::catalogue-item-id db)))))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-organization (fn [db _] (get-in db [::form :organization])))
(rf/reg-event-db ::set-selected-organization (fn [db [_ organization]] (assoc-in db [::form :organization] organization)))

(rf/reg-sub ::selected-workflow (fn [db _] (get-in db [::form :workflow])))
(rf/reg-event-db ::set-selected-workflow (fn [db [_ workflow]] (assoc-in db [::form :workflow] workflow)))

(rf/reg-sub ::selected-resource (fn [db _] (get-in db [::form :resource])))
(rf/reg-event-db ::set-selected-resource (fn [db [_ resource]] (assoc-in db [::form :resource] resource)))

(rf/reg-sub ::selected-form (fn [db _] (get-in db [::form :form])))
(rf/reg-event-db ::set-selected-form (fn [db [_ form]] (assoc-in db [::form :form] form)))

(defn- valid-localization? [localization]
  (not (str/blank? (:title localization))))

(defn- valid-request? [form request languages]
  (let [selected-organization (:organization request)]
    (and (= (get-in form [:workflow :organization]) selected-organization)
         (= (get-in form [:resource :organization]) selected-organization)
         (= (get-in form [:form :form/organization]) selected-organization)
         (number? (:wfid request))
         (number? (:resid request))
         (number? (:form request))
         (= (set languages)
            (set (keys (:localizations request))))
         (every? valid-localization? (vals (:localizations request))))))

(defn- empty-string-to-nil [str]
  (when-not (str/blank? str)
    str))

(defn build-request [form languages]
  (let [request {:wfid (get-in form [:workflow :id])
                 :resid (get-in form [:resource :id])
                 :form (get-in form [:form :form/id])
                 :organization (:organization form)
                 :localizations (into {}
                                      (for [lang languages]
                                        [lang {:title (trim-when-string (get-in form [:title lang]))
                                               :infourl (-> (get-in form [:infourl lang])
                                                            empty-string-to-nil
                                                            trim-when-string)}]))}]
    (when (valid-request? form request languages)
      request)))

(defn- page-title [editing?]
  (if editing?
    (text :t.administration/edit-catalogue-item)
    (text :t.administration/create-catalogue-item)))

(defn- create-catalogue-item! [_ [_ request]]
  (let [description [text :t.administration/create-catalogue-item]]
    (post! "/api/catalogue-items/create"
           {:params (-> request
                        ;; create disabled catalogue items by default
                        (assoc :enabled false))
            :handler (flash-message/default-success-handler
                      :top
                      description
                      (fn [response]
                        (navigate! (str "/administration/catalogue-items/"
                                        (:id response)))))
            :error-handler (flash-message/default-error-handler :top description)}))
  {})

(defn- edit-catalogue-item! [{:keys [db]} [_ request]]
  (let [id (::catalogue-item-id db)
        description [text :t.administration/edit-catalogue-item]]
    (put! "/api/catalogue-items/edit"
          {:params {:id id
                    :localizations (:localizations request)}
           :handler (flash-message/default-success-handler
                     :top
                     description
                     (fn [_]
                       (navigate! (str "/administration/catalogue-items/" id))))
           :error-handler (flash-message/default-error-handler :top description)}))
  {})

(rf/reg-event-fx ::create-catalogue-item create-catalogue-item!)
(rf/reg-event-fx ::edit-catalogue-item edit-catalogue-item!)

(defn- fetch-workflows []
  (fetch "/api/workflows"
         {:handler #(rf/dispatch [::fetch-workflows-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch workflows")}))

(rf/reg-fx ::fetch-workflows fetch-workflows)

(rf/reg-event-fx
 ::fetch-workflows-result
 (fn [{:keys [db]} [_ workflows]]
   (-> (assoc db ::workflows workflows)
       (update-loading))))

(rf/reg-sub ::workflows (fn [db _] (::workflows db)))

(defn- fetch-resources []
  (fetch "/api/resources"
         {:handler #(rf/dispatch [::fetch-resources-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch resources")}))

(rf/reg-fx ::fetch-resources fetch-resources)

(rf/reg-event-fx
 ::fetch-resources-result
 (fn [{:keys [db]} [_ resources]]
   (-> (assoc db ::resources resources)
       (update-loading))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))


(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(rf/reg-fx ::fetch-forms fetch-forms)

(rf/reg-event-fx
 ::fetch-forms-result
 (fn [{:keys [db]} [_ forms]]
   (-> (assoc db ::forms forms)
       (update-loading))))

(rf/reg-sub ::forms (fn [db _] (::forms db)))


(defn- fetch-catalogue-item [id]
  (fetch (str "/api/catalogue-items/" id)
         {:handler #(rf/dispatch [::fetch-catalogue-item-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch catalogue item")}))

(rf/reg-fx
 ::fetch-catalogue-item
 (fn [id]
   (fetch-catalogue-item id)))

(defn- item-by-id [items id-key id]
  (first (filter #(= (id-key %) id) items)))

(rf/reg-event-db
 ::fetch-catalogue-item-result
 (fn [db [_ {:keys [wfid resource-id formid localizations organization]}]]
   (-> db
       (assoc ::form {:workflow (item-by-id (::workflows db) :id wfid)
                      :resource (item-by-id (::resources db) :id resource-id)
                      :form (item-by-id (::forms db) :form/id formid)
                      :organization organization
                      :title (map-vals :title localizations)
                      :infourl (map-vals :infourl localizations)})
       (dissoc ::loading-catalogue-item?
               ::loading?))))

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private organization-dropdown-id "organization-dropdown")
(def ^:private workflow-dropdown-id "workflow-dropdown")
(def ^:private resource-dropdown-id "resource-dropdown")
(def ^:private form-dropdown-id "form-dropdown")

(defn- list-organizations []
  (let [workflows @(rf/subscribe [::workflows])
        resources @(rf/subscribe [::resources])
        forms @(rf/subscribe [::forms])
        items-to-organizations (fn [items key]
                                 (set (distinct (mapv key items))))]
    (sort
     (vec (set/intersection (items-to-organizations workflows :organization)
                            (items-to-organizations resources :organization)
                            (items-to-organizations forms :form/organization))))))

(defn- catalogue-item-organization-field []
  (let [organizations (list-organizations)
        selected-organization @(rf/subscribe [::selected-organization])
        item-selected? #(= % selected-organization)
        readonly (or @(rf/subscribe [::editing?])
                     (roles/disallow-setting-organization? (:roles @(rf/subscribe [:identity]))))]
    [:div.form-group
     [:label {:for organization-dropdown-id} (text :t.administration/organization)]
     (if readonly
       (let [organization (first (filter item-selected? organizations))]
         [fields/readonly-field {:id organization-dropdown-id
                                 :value organization}])
       [dropdown/dropdown
        {:id organization-dropdown-id
         :items organizations
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-organization %])}])]))

(defn- catalogue-item-title-field [language]
  [text-field context {:keys [:title language]
                       :label (str (text :t.create-catalogue-item/title)
                                   " (" (str/upper-case (name language)) ")")
                       :placeholder (text :t.create-catalogue-item/title-placeholder)}])

(defn- catalogue-item-infourl-field [language]
  [text-field context {:keys [:infourl language]
                       ;; no placeholder to make clear that field is optional
                       :label (str (text :t.create-catalogue-item/infourl)
                                   " (" (str/upper-case (name language)) ")")}])

(defn- catalogue-item-workflow-field []
  (let [organization @(rf/subscribe [::selected-organization])
        workflows @(rf/subscribe [::workflows])
        compatible-workflows (filter #(= organization (% :organization)) workflows)
        editing? @(rf/subscribe [::editing?])
        selected-workflow @(rf/subscribe [::selected-workflow])
        item-selected? #(= (:id %) (:id selected-workflow))]
    [:div.form-group
     [:label {:for workflow-dropdown-id} (text :t.create-catalogue-item/workflow-selection)]
     (if editing?
       (let [workflow (item-by-id compatible-workflows :id (:id selected-workflow))]
         [fields/readonly-field {:id workflow-dropdown-id
                                 :value (:title workflow)}])
       [dropdown/dropdown
        {:id workflow-dropdown-id
         :items compatible-workflows
         :item-key :id
         :item-label :title
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-workflow %])}])]))

(defn- catalogue-item-resource-field []
  (let [organization @(rf/subscribe [::selected-organization])
        resources @(rf/subscribe [::resources])
        compatible-resources (filter #(= organization (% :organization)) resources)
        editing? @(rf/subscribe [::editing?])
        selected-resource @(rf/subscribe [::selected-resource])
        item-selected? #(= (:id %) (:id selected-resource))]
    [:div.form-group
     [:label {:for resource-dropdown-id} (text :t.create-catalogue-item/resource-selection)]
     (if editing?
       (let [resource (item-by-id compatible-resources :id (:id selected-resource))]
         [fields/readonly-field {:id resource-dropdown-id
                                 :value (:resid resource)}])
       [dropdown/dropdown
        {:id resource-dropdown-id
         :items compatible-resources
         :item-key :id
         :item-label :resid
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-resource %])}])]))

(defn- catalogue-item-form-field []
  (let [organization @(rf/subscribe [::selected-organization])
        forms @(rf/subscribe [::forms])
        compatible-forms (filter #(= organization (% :form/organization)) forms)
        editing? @(rf/subscribe [::editing?])
        selected-form @(rf/subscribe [::selected-form])
        item-selected? #(= (:form/id %) (:form/id selected-form))]
    [:div.form-group
     [:label {:for form-dropdown-id} (text :t.create-catalogue-item/form-selection)]
     (if editing?
       (let [form (item-by-id compatible-forms :form/id (:form/id selected-form))]
         [fields/readonly-field {:id form-dropdown-id
                                 :value (:form/title form)}])
       [dropdown/dropdown
        {:id form-dropdown-id
         :items compatible-forms
         :item-key :form/id
         :item-label :form/title
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-form %])}])]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/catalogue-items"
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
     [administration/navigator]
     [document-title (page-title editing?)]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-catalogue-item"
       :title (page-title editing?)
       :always [:div
                (if loading?
                  [:div#catalogue-item-loader [spinner/big]]
                  [:div#catalogue-item-editor
                   [catalogue-item-organization-field]
                   (for [language languages]
                     [:<> {:key language}
                      [catalogue-item-title-field language]
                      [catalogue-item-infourl-field language]])
                   [catalogue-item-workflow-field]
                   [catalogue-item-resource-field]
                   [catalogue-item-form-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-catalogue-item-button form languages editing?]]])]}]]))
