(ns rems.administration
  (:require [ajax.core :refer [GET PUT]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! redirect-when-unauthorized]]))

;; TODO copypaste from rems.catalogue, move to rems.db.catalogue?

(defn- simple-fetch [path dispatch]
  (GET path {:handler dispatch
             :error-handler redirect-when-unauthorized
             :response-format :json
             :keywords? true}))

(defn- fetch-catalogue []
  (simple-fetch "/api/catalogue-items/" #(rf/dispatch [::fetch-catalogue-result %])))

(defn- fetch-workflows []
  (simple-fetch "/api/workflows/?active=true" #(rf/dispatch [::set-workflows %])))

(defn- fetch-resources []
  (simple-fetch "/api/resources/?active=true" #(rf/dispatch [::set-resources %])))

(defn- fetch-forms []
  (simple-fetch "/api/forms/?active=true" #(rf/dispatch [::set-forms %])))

(defn- fetch-licenses []
  (simple-fetch "/api/licenses/" #(do (rf/dispatch [::set-licenses %])
                                      (rf/dispatch [::set-selected-licenses #{}]))))

(rf/reg-fx
 ::fetch-catalogue
 (fn [_]
   (fetch-catalogue)))

(rf/reg-event-fx
 ::start-fetch-catalogue
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue []}))

(rf/reg-event-db
 ::reset-create-catalogue-item
 (fn [db _]
   (dissoc db ::title ::selected-workflow ::selected-resource ::selected-form)))

(rf/reg-event-db
 ::reset-create-resource
 (fn [db _]
   (dissoc db ::prefix ::resid ::selected-licenses)))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-event-db
 ::set-prefix
 (fn [db [_ prefix]]
   (assoc db ::prefix prefix)))

(rf/reg-event-db
 ::set-resid
 (fn [db [_ resid]]
   (assoc db ::resid resid)))

(rf/reg-event-db
 ::set-title
 (fn [db [_ title]]
   (assoc db ::title title)))

(rf/reg-event-db
 ::set-workflows
 (fn [db [_ workflows]]
   (assoc db ::workflows workflows)))

(rf/reg-event-db
 ::set-resources
 (fn [db [_ resources]]
   (assoc db ::resources resources)))

(rf/reg-event-db
 ::set-forms
 (fn [db [_ forms]]
   (assoc db ::forms forms)))

(rf/reg-event-db
 ::set-licenses
 (fn [db [_ licenses]]
   (assoc db ::licenses licenses)))

(rf/reg-event-db
 ::set-selected-workflow
 (fn [db [_ workflow]]
   (if workflow
     (assoc db ::selected-workflow ^{:key (:id workflow)} workflow )
     (dissoc db ::selected-workflow))))

(rf/reg-event-db
 ::set-selected-resource
 (fn [db [_ resource]]
   (if resource
     (assoc db ::selected-resource ^{:key (:id resource)} resource )
     (dissoc db ::selected-resource))))

(rf/reg-event-db
 ::set-selected-form
 (fn [db [_ form]]
   (if form
     (assoc db ::selected-form ^{:key (:id form)} form )
     (dissoc db ::selected-form))))

(rf/reg-event-db
 ::set-selected-licenses
 (fn [db [_ licenses]]
   (assoc db ::selected-licenses licenses)))

(rf/reg-event-db
 ::add-selected-licenses
 (fn [db [_ license]]
   (if (contains? (::selected-licenses db) license)
     db
     (update db ::selected-licenses conj license))))

(rf/reg-event-db
 ::remove-selected-licenses
 (fn [db [_ license]]
   (update db ::selected-licenses disj license)))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(rf/reg-sub
 ::prefix
 (fn [db _]
   (::prefix db)))

(rf/reg-sub
 ::resid
 (fn [db _]
   (::resid db)))

(rf/reg-sub
 ::title
 (fn [db _]
   (::title db)))

(rf/reg-sub
 ::selected-workflow
 (fn [db _]
   (::selected-workflow db)))

(rf/reg-sub
 ::selected-resource
 (fn [db _]
   (::selected-resource db)))

(rf/reg-sub
 ::selected-form
 (fn [db _]
   (::selected-form db)))

(rf/reg-sub
 ::selected-licenses
 (fn [db _]
  (::selected-licenses db)))

(rf/reg-sub
 ::workflows
 (fn [db _]
   (::workflows db)))

(rf/reg-sub
 ::resources
 (fn [db _]
   (::resources db)))

(rf/reg-sub
 ::forms
 (fn [db _]
   (::forms db)))

(rf/reg-sub
 ::licenses
 (fn [db _]
   (::licenses db)))

(defn- update-catalogue-item [id state]
  (PUT "/api/catalogue-items/update" {:format :json
                                      :params {:id id :state state}
                                      ;; TODO error handling
                                      :error-handler redirect-when-unauthorized
                                      :handler (fn [resp]
                                                 (rf/dispatch [::start-fetch-catalogue]))}))

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ id state]]
   (update-catalogue-item id state)
   {}))

(defn- create-resource [prefix resid licenses]
  (PUT "/api/resources/create" {:format :json
                               :params {:prefix prefix
                                        :resid resid
                                        :licenses (if licenses
                                                    (map :id licenses)
                                                    [])}
                               :error-handler redirect-when-unauthorized
                               :handler (fn [resp]
                                          (dispatch! "#/administration"))}))

(defn- create-catalogue-item [title workflow resource form]
  (PUT "/api/catalogue-items/create" {:format :json
                                      :params {:title title
                                               :wfid (:id workflow)
                                               :resid (:id resource)
                                               :form (:id form)}
                                      ;; TODO error handling
                                      :error-handler redirect-when-unauthorized
                                      :handler (fn [resp]
                                                 (dispatch! "#/administration"))}))

(rf/reg-event-fx
 ::create-resource
 (fn [db [_ prefix resid licenses]]
   (create-resource prefix resid licenses)
   {}))

(rf/reg-event-fx
 ::create-catalogue-item
 (fn [db [_ title workflow resource form]]
   (create-catalogue-item title workflow resource form)
   {}))

;;;; UI ;;;;

(defn- disable-button [item]
  [:button.btn.btn-secondary
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "disabled"])}
   (text :t.administration/disable)])

(defn- enable-button [item]
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "enabled"])}
   (text :t.administration/enable)])

(defn- to-create-resource-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-resource")}
   (text :t.administration/create-resource)])

(defn- to-create-catalogue-item-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-catalogue-item")}
   (text :t.administration/create-catalogue-item)])

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.create-catalogue-item/cancel)])

(defn- save-resource-button []
  (let [prefix @(rf/subscribe [::prefix])
        resid @(rf/subscribe [::resid])
        licenses @(rf/subscribe [::selected-licenses])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-resource prefix resid licenses])
      :disabled (not (and (not (str/blank? prefix)) (not (str/blank? resid))))}
     (text :t.create-resource/save)]))

(defn- save-catalogue-item-button [item]
  (let [title @(rf/subscribe [::title])
        workflow @(rf/subscribe [::selected-workflow])
        resource @(rf/subscribe [::selected-resource])
        form @(rf/subscribe [::selected-form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-catalogue-item title workflow resource form])
      :disabled (not (and (not (str/blank? title)) workflow resource form))}
     (text :t.create-catalogue-item/save)]))

(defn- catalogue-item-button [item]
  (if (disabled-catalogue-item? item)
    [enable-button item]
    [disable-button item]))

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :commands {:value catalogue-item-button
              :sortable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language]
  ;; TODO no sorting yet
  [table/component (catalogue-columns language) [:name :commands]
   [:name :asc] (fn [_])
   :id items])

(defn administration-page []
  (let [catalogue (rf/subscribe [::catalogue])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [:h2 (text :t.navigation/administration)]]
            (if @loading?
              [[spinner/big]]
              [[:div
                [:div.col.commands
                 [to-create-resource-button]
                 [to-create-catalogue-item-button]]
                [catalogue-list @catalogue @language]]])))))

(defn create-resource-page []
  (fetch-licenses)
  (let [prefix (rf/subscribe [::prefix])
        resid (rf/subscribe [::resid])
        licenses (rf/subscribe [::licenses])
        selected-licenses (rf/subscribe [::selected-licenses])]
    (fn []
    [collapsible/component
     {:id "create-create"
      :title (text :t.navigation/create-resource)
      :always [:div
               [:div.form-group.field
                [:label {:for "prefix"} (text :t.create-resource/prefix)]
                [:input.form-control {:name "prefix"
                                      :type :text
                                      :placeholder (text :t.create-resource/prefix-placeholder)
                                      :value @prefix
                                      :on-change #(rf/dispatch [::set-prefix (.. % -target -value)])}]]
               [:div.form-group.field
                [:label {:for "resid"} (text :t.create-resource/resid)]
                [:input.form-control {:name "resid"
                                      :type :text
                                      :placeholder (text :t.create-resource/resid-placeholder)
                                      :value @resid
                                      :on-change #(rf/dispatch [::set-resid (.. % -target -value)])}]]
               [:div.form-group
                [:label (text :t.create-resource/licenses-selection)]
                [autocomplete/component
                 {:value (sort-by :id @selected-licenses)
                  :items @licenses
                  :value->text #(:title %2)
                  :item->key :id
                  :item->text :title
                  :item->value identity
                  :search-fields [:title]
                  :add-fn #(rf/dispatch [::add-selected-licenses %])
                  :remove-fn #(rf/dispatch [::remove-selected-licenses %])}]]
               [:div.col.commands
                 [cancel-button]
                 [save-resource-button]]]}])))

(defn create-catalogue-item-page []
  (fetch-workflows)
  (fetch-resources)
  (fetch-forms)
  (let [workflows (rf/subscribe [::workflows])
        resources (rf/subscribe [::resources])
        forms (rf/subscribe [::forms])
        title (rf/subscribe [::title])
        selected-workflow (rf/subscribe [::selected-workflow])
        selected-resource (rf/subscribe [::selected-resource])
        selected-form (rf/subscribe [::selected-form])]
    (fn []
      [collapsible/component
       {:id "create-catalogue-item"
        :title (text :t.navigation/create-catalogue-item)
        :always [:div
                 [:div.form-group.field
                  [:label {:for "title"} (text :t.create-catalogue-item/title)]
                  [:input.form-control {:name "title"
                                        :type :text
                                        :placeholder (text :t.create-catalogue-item/title-placeholder)
                                        :value @title
                                        :on-change #(rf/dispatch [::set-title (.. % -target -value)])}]]
                 [:div.form-group
                  [:label (text :t.create-catalogue-item/workflow-selection)]
                  [autocomplete/component
                   {:value (when @selected-workflow #{@selected-workflow})
                    :items @workflows
                    :value->text #(:title %2)
                    :item->key :id
                    :item->text :title
                    :item->value identity
                    :search-fields [:title]
                    :add-fn #(rf/dispatch [::set-selected-workflow %])
                    :remove-fn #(rf/dispatch [::set-selected-workflow nil])}]]
                 [:div.form-group
                  [:label (text :t.create-catalogue-item/resource-selection)]
                  [autocomplete/component
                   {:value (when @selected-resource #{@selected-resource})
                    :items @resources
                    :value->text #(:resid %2)
                    :item->key :id
                    :item->text :resid
                    :item->value identity
                    :search-fields [:resid]
                    :add-fn #(rf/dispatch [::set-selected-resource %])
                    :remove-fn #(rf/dispatch [::set-selected-resource nil])}]]
                 [:div.form-group
                  [:label (text :t.create-catalogue-item/form-selection)]
                  [autocomplete/component
                   {:value (when @selected-form #{@selected-form})
                    :items @forms
                    :value->text #(:title %2)
                    :item->key :id
                    :item->text :title
                    :item->value identity
                    :search-fields [:title]
                    :add-fn #(rf/dispatch [::set-selected-form %])
                    :remove-fn #(rf/dispatch [::set-selected-form nil])}]]
                 [:div.col.commands
                  [cancel-button]
                  [save-catalogue-item-button]]]}])))
