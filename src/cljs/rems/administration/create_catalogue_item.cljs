(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [medley.core :refer [find-first map-vals]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field organization-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.config]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.globals]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format localized get-localized-title]]
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
                 [::categories]
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

(rf/reg-sub ::selected-categories (fn [db _] (get-in db [::form :categories])))
(rf/reg-event-db ::set-selected-categories (fn [db [_ categories]] (assoc-in db [::form :categories] categories)))

(defn- valid-localization? [localization]
  (not (str/blank? (:title localization))))

(defn- valid-request? [request]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (number? (:wfid request))
       (number? (:resid request))
       (or (nil? (:form request))
           (number? (:form request)))
       (= (set @rems.config/languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

(defn- empty-string-to-nil [str]
  (when-not (str/blank? str)
    str))

(defn build-request [form]
  (let [request {:wfid (get-in form [:workflow :id])
                 :resid (get-in form [:resource :id])
                 :form (get-in form [:form :form/id])
                 :organization {:organization/id (get-in form [:organization :organization/id])}
                 :localizations (into {}
                                      (for [lang @rems.config/languages]
                                        [lang {:title (trim-when-string (get-in form [:title lang]))
                                               :infourl (-> (get-in form [:infourl lang])
                                                            empty-string-to-nil
                                                            trim-when-string)}]))
                 :categories (mapv #(select-keys % [:category/id]) (get-in form [:categories]))}]
    (when (valid-request? request)
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
                    :organization (:organization request)
                    :localizations (:localizations request)
                    :categories (:categories request)}
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
    (when (::editing? db)
      (when-let [{:keys [wfid resource-id formid localizations organization categories]} (get-in db [::catalogue-item :data])]
        (when-let [workflows (get-in db [::workflows :data])]
          (when-let [resources (get-in db [::resources :data])]
            (when-let [forms (get-in db [::forms :data])]
              {::form {:workflow (item-by-id workflows :id wfid)
                       :resource (item-by-id resources :id resource-id)
                       :form (item-by-id forms :form/id formid)
                       :organization organization
                       :title (map-vals :title localizations)
                       :infourl (map-vals :infourl localizations)
                       :categories categories}}))))))))

(fetcher/reg-fetcher ::workflows "/api/workflows" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::resources "/api/resources" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::forms "/api/forms" {:on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::catalogue-item "/api/catalogue-items/:id" {:path-params (fn [db] {:id (::catalogue-item-id db)})
                                                                  :on-success #(rf/dispatch [::update-loading!])})
(fetcher/reg-fetcher ::categories "/api/categories")

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private workflow-dropdown-id "workflow-dropdown")
(def ^:private resource-dropdown-id "resource-dropdown")
(def ^:private form-dropdown-id "form-dropdown")
(def ^:private categories-dropdown-id "categories-dropdown")

(defn- catalogue-item-organization-field []
  [organization-field context {:keys [:organization]}])

(defn- catalogue-item-title-field []
  [localized-text-field context {:keys [:title]
                                 :label (text :t.administration/title)}])

(defn- catalogue-item-infourl-field []
  [localized-text-field context {:keys [:infourl]
                                 :label (text-format :t.label/optional (text :t.administration/more-info))}])

(defn- localize-org-short [x]
  (when-let [org-short (get-in x [:organization :organization/short-name])]
    (text-format :t.label/default
                 (text :t.administration/org)
                 (localized org-short))))

(defn- catalogue-item-workflow-field []
  (let [workflows @(rf/subscribe [::workflows])
        editing? @(rf/subscribe [::editing?])
        selected-workflow @(rf/subscribe [::selected-workflow])
        item-selected? #(= (:id %) (:id selected-workflow))]
    [:div.form-group
     [:label.administration-field-label {:for workflow-dropdown-id} (text :t.administration/workflow)]
     (if editing?
       (let [workflow (item-by-id workflows :id (:id selected-workflow))]
         [fields/readonly-field {:id workflow-dropdown-id
                                 :value (:title workflow)}])
       [dropdown/dropdown
        {:id workflow-dropdown-id
         :items (->> workflows
                     (filter :enabled)
                     (remove :archived)
                     (mapv #(assoc % ::label (text-format :t.label/parens (:title %) (localize-org-short %)))))
         :item-key :id
         :item-label ::label
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-workflow (dissoc % ::label)])}])]))

(defn- localize-licenses [resource]
  (when-some [licenses (->> (:licenses resource)
                            (map #(get-localized-title %))
                            seq)]
    (text-format :t.label/default
                 (text :t.administration/licenses)
                 (str/join ", " licenses))))

(defn- catalogue-item-resource-field []
  (let [resources @(rf/subscribe [::resources])
        editing? @(rf/subscribe [::editing?])
        selected-resource (:id @(rf/subscribe [::selected-resource]))]
    [:div.form-group
     [:label.administration-field-label {:for resource-dropdown-id} (text :t.administration/resource)]
     (if editing?
       [fields/readonly-field {:id resource-dropdown-id
                               :value (->> selected-resource
                                           (item-by-id resources :id)
                                           :resid)}]
       (let [resid-counts (frequencies (map :resid resources))
             has-duplicate-resid? #(> (get resid-counts (:resid %)) 1)]
         [dropdown/dropdown
          {:id resource-dropdown-id
           :items (->> resources
                       (filter :enabled)
                       (remove :archived)
                       (mapv #(assoc % ::label (str (:resid %)
                                                    (andstr " (" (localize-org-short %) ")")
                                                    (andstr " (" (when (has-duplicate-resid? %) (localize-licenses %)) ")")))))
           :item-key :id
           :item-label ::label
           :item-selected? #(= selected-resource (:id %))
           :on-change #(rf/dispatch [::set-selected-resource (dissoc % ::label)])}]))]))

(defn- catalogue-item-form-field []
  (let [forms @(rf/subscribe [::forms])
        editing? @(rf/subscribe [::editing?])
        selected-form @(rf/subscribe [::selected-form])
        item-selected? #(= (:form/id %) (:form/id selected-form))]
    [:div.form-group
     [:label.administration-field-label {:for form-dropdown-id}
      (text-format :t.label/optional (text :t.administration/form))]
     (if editing?
       (let [form (item-by-id forms :form/id (:form/id selected-form))]
         [fields/readonly-field {:id form-dropdown-id
                                 :value (:form/internal-name form)}])
       [dropdown/dropdown
        {:id form-dropdown-id
         :items (->> forms
                     (filter :enabled)
                     (remove :archived)
                     (mapv #(assoc % ::label (text-format :t.label/parens (:form/internal-name %) (localize-org-short %)))))
         :item-key :form/id
         :item-label ::label
         :item-selected? item-selected?
         :clearable? true
         :placeholder (text :t.administration/no-form)
         :on-change #(rf/dispatch [::set-selected-form (dissoc % ::label)])}])]))

(defn- catalogue-item-categories-field []
  (let [categories @(rf/subscribe [::categories])
        selected-categories (set (mapv :category/id @(rf/subscribe [::selected-categories])))
        item-selected? #(contains? selected-categories (:category/id %))]
    [:div.form-group
     [:label.administration-field-label {:for categories-dropdown-id}
      (cond->> (text :t.administration/categories)
        (not (:enable-catalogue-tree @rems.globals/config)) (text-format :t.label/optional))]
     [dropdown/dropdown
      {:id categories-dropdown-id
       :items (->> categories
                   (mapv #(assoc % ::label (localized (:category/title %)))))
       :multi? true
       :item-key :category/id
       :item-label ::label
       :item-selected? item-selected?
       :clearable? true
       :placeholder (text :t.administration/no-categories)
       :on-change (fn [items] (rf/dispatch [::set-selected-categories (mapv #(dissoc % ::label) items)]))}]]))

(defn- cancel-button [catalogue-item-id]
  [atoms/link {:id :cancel
               :class "btn btn-secondary"}
   (if catalogue-item-id
     (str "/administration/catalogue-items/" catalogue-item-id)
     "/administration/catalogue-items")
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [form editing?]
  (let [request (build-request form)]
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
  (let [editing? @(rf/subscribe [::editing?])
        catalogue-item-id (when editing? @(rf/subscribe [::catalogue-item-id]))
        loading? (or @(rf/subscribe [::workflows :fetching?])
                     @(rf/subscribe [::resources :fetching?])
                     @(rf/subscribe [::forms :fetching?])
                     @(rf/subscribe [::catalogue-item :fetching?])
                     @(rf/subscribe [::categories :fetching?]))
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
                   [catalogue-item-title-field]
                   [catalogue-item-infourl-field]
                   [catalogue-item-workflow-field]
                   [catalogue-item-resource-field]
                   [catalogue-item-form-field]
                   [catalogue-item-categories-field]

                   [:div.col.commands
                    [cancel-button catalogue-item-id]
                    [save-catalogue-item-button form editing?]]])]}]]))
