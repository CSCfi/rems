(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [medley.core :refer [find-first map-vals]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [navigate! post! put! trim-when-string]]))

(defn- item-by-id [items id-key id]
  (find-first #(= (id-key %) id) items))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ catalogue-item-id]]
   {:db (assoc db
               ::form nil
               ::catalogue-item-id catalogue-item-id
               ::editing? (some? catalogue-item-id))
    :dispatch-n [[::workflows {:disabled true :archived true}]
                 [::resources {:disabled true :archived true}]
                 [::forms {:disabled true :archived true}]
                 (when catalogue-item-id [::catalogue-item])]}))

(rf/reg-sub ::catalogue-item-id (fn [db _] (::catalogue-item-id db)))
(rf/reg-sub ::editing? (fn [db _] (::editing? db)))
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-workflow (fn [db _] (get-in db [::form :workflow])))
(rf/reg-event-db ::set-selected-workflow (fn [db [_ workflow]] (assoc-in db [::form :workflow] workflow)))

(rf/reg-sub ::selected-resource (fn [db _] (get-in db [::form :resource])))
(rf/reg-event-db ::set-selected-resource (fn [db [_ resource]] (assoc-in db [::form :resource] resource)))

(rf/reg-sub ::selected-form (fn [db _] (get-in db [::form :form])))
(rf/reg-event-db ::set-selected-form (fn [db [_ form]] (assoc-in db [::form :form] form)))

(defn- valid-localization? [localization]
  (not (str/blank? (:title localization))))

(defn- valid-request? [form request languages]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (number? (:wfid request))
       (number? (:resid request))
       (number? (:form request))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

(defn- empty-string-to-nil [str]
  (when-not (str/blank? str)
    str))

(defn build-request [form languages]
  (let [request {:wfid (get-in form [:workflow :id])
                 :resid (get-in form [:resource :id])
                 :form (get-in form [:form :form/id])
                 :organization {:organization/id (get-in form [:organization :organization/id])}
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

(rf/reg-event-db
 ::update-loading!
 (fn [db _]
   (merge
    db
    (when-let [{:keys [wfid resource-id formid localizations organization]} (get-in db [::catalogue-item :data])]
      (when-let [workflows (get-in db [::workflows :data])]
        (when-let [resources (get-in db [::resources :data])]
          (when-let [forms (get-in db [::forms :data])]
            {::form {:workflow (item-by-id workflows :id wfid)
                     :resource (item-by-id resources :id resource-id)
                     :form (item-by-id forms :form/id formid)
                     :organization organization
                     :title (map-vals :title localizations)
                     :infourl (map-vals :infourl localizations)}})))))))

(fetcher/reg-fetcher ::workflows "/api/workflows" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::resources "/api/resources" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::forms "/api/forms" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::catalogue-item "/api/catalogue-items/:id" {:path-params (fn [db] {:id (::catalogue-item-id db)})
                                                                  :on-success #(rf/dispatch [::update-loading!])})

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private organization-dropdown-id "organization-dropdown")
(def ^:private workflow-dropdown-id "workflow-dropdown")
(def ^:private resource-dropdown-id "resource-dropdown")
(def ^:private form-dropdown-id "form-dropdown")

(rf/reg-sub ::selected-organization (fn [db _] (get-in db [::form :organization])))
(rf/reg-event-db ::set-selected-organization (fn [db [_ organization]] (assoc-in db [::form :organization] organization)))

(defn- catalogue-item-organization-field []
  [fields/organization-field {:id "organization-dropdown"
                              :value @(rf/subscribe [::selected-organization])
                              :on-change #(rf/dispatch [::set-selected-organization %])}])

(defn- catalogue-item-title-field [language]
  [text-field context {:keys [:title language]
                       :label (str (text :t.administration/title)
                                   " (" (str/upper-case (name language)) ")")
                       :placeholder (text :t.administration/title)}])

(defn- catalogue-item-infourl-field [language]
  [text-field context {:keys [:infourl language]
                       ;; no placeholder to make clear that field is optional
                       :label (str (text :t.administration/more-info)
                                   " (" (str/upper-case (name language)) ")")}])

(defn- catalogue-item-workflow-field []
  (let [workflows @(rf/subscribe [::workflows])
        editing? @(rf/subscribe [::editing?])
        selected-workflow @(rf/subscribe [::selected-workflow])
        item-selected? #(= (:id %) (:id selected-workflow))
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for workflow-dropdown-id} (text :t.administration/workflow)]
     (if editing?
       (let [workflow (item-by-id workflows :id (:id selected-workflow))]
         [fields/readonly-field {:id workflow-dropdown-id
                                 :value (:title workflow)}])
       [dropdown/dropdown
        {:id workflow-dropdown-id
         :items (->> workflows (filter :enabled) (remove :archived))
         :item-key :id
         :item-label #(str (:title %)
                           " (org: "
                           (get-in % [:organization :organization/short-name language])
                           ")")
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-workflow %])}])]))

(defn- catalogue-item-resource-field []
  (let [resources @(rf/subscribe [::resources])
        editing? @(rf/subscribe [::editing?])
        selected-resource @(rf/subscribe [::selected-resource])
        item-selected? #(= (:id %) (:id selected-resource))
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for resource-dropdown-id} (text :t.administration/resource)]
     (if editing?
       (let [resource (item-by-id resources :id (:id selected-resource))]
         [fields/readonly-field {:id resource-dropdown-id
                                 :value (:resid resource)}])
       [dropdown/dropdown
        {:id resource-dropdown-id
         :items (->> resources (filter :enabled) (remove :archived))
         :item-key :id
         :item-label #(str (:resid %)
                           " (org: "
                           (get-in % [:organization :organization/short-name language])
                           ")")
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-resource %])}])]))

(defn- catalogue-item-form-field []
  (let [forms @(rf/subscribe [::forms])
        editing? @(rf/subscribe [::editing?])
        selected-form @(rf/subscribe [::selected-form])
        item-selected? #(= (:form/id %) (:form/id selected-form))
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for form-dropdown-id} (text :t.administration/form)]
     (if editing?
       (let [form (item-by-id forms :form/id (:form/id selected-form))]
         [fields/readonly-field {:id form-dropdown-id
                                 :value (:form/title form)}])
       [dropdown/dropdown
        {:id form-dropdown-id
         :items (->> forms (filter :enabled) (remove :archived))
         :item-key :form/id
         :item-label #(str (:form/title %)
                           " (org: "
                           (get-in % [:organization :organization/short-name language])
                           ")")
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-form %])}])]))

(defn- cancel-button [catalogue-item-id]
  [atoms/link {:id :cancel
               :class "btn btn-secondary"}
   (if catalogue-item-id
     (str "/administration/catalogue-items/" catalogue-item-id)
     "/administration/catalogue-items")
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [form languages editing?]
  (let [request (build-request form languages)]
    [:button.btn.btn-primary
     {:type :button
      :id :save
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
        catalogue-item-id (when editing? @(rf/subscribe [::catalogue-item-id]))
        loading? (or @(rf/subscribe [::workflows ::fetching?])
                     @(rf/subscribe [::resources ::fetching?])
                     @(rf/subscribe [::forms ::fetching?])
                     @(rf/subscribe [::catalogue-item ::fetching?]))
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
                  [:div#catalogue-item-editor.fields
                   [catalogue-item-organization-field]
                   (for [language languages]
                     [:<> {:key language}
                      [catalogue-item-title-field language]
                      [catalogue-item-infourl-field language]])
                   [catalogue-item-workflow-field]
                   [catalogue-item-resource-field]
                   [catalogue-item-form-field]

                   [:div.col.commands
                    [cancel-button catalogue-item-id]
                    [save-catalogue-item-button form languages editing?]]])]}]]))
