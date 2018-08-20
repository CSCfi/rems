(ns rems.administration.catalogue
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(defn- fetch-workflows []
  (fetch "/api/workflows/?active=true" {:handler #(rf/dispatch [::set-workflows %])}))

(defn- fetch-resources []
  (fetch "/api/resources/?active=true" {:handler #(rf/dispatch [::set-resources %])}))

(defn- fetch-forms []
  (fetch "/api/forms/?active=true" {:handler #(rf/dispatch [::set-forms %])}))

(defn- create-catalogue-item [title workflow resource form]
  (put! "/api/catalogue-items/create" {:params {:title title
                                                :wfid (:id workflow)
                                                :resid (:id resource)
                                                :form (:id form)}
                                       ;; TODO error handling
                                       :handler (fn [resp] (dispatch! "#/administration"))}))

(rf/reg-sub
 ::workflows
 (fn [db _]
   (::workflows db)))

(rf/reg-event-db
 ::set-workflows
 (fn [db [_ workflows]]
   (assoc db ::workflows workflows)))

(rf/reg-sub
 ::resources
 (fn [db _]
   (::resources db)))

(rf/reg-event-db
 ::set-resources
 (fn [db [_ resources]]
   (assoc db ::resources resources)))

(rf/reg-sub
 ::forms
 (fn [db _]
   (::forms db)))

(rf/reg-event-db
 ::set-forms
 (fn [db [_ forms]]
   (assoc db ::forms forms)))

(rf/reg-sub
 ::title
 (fn [db _]
   (::title db)))

(rf/reg-event-db
 ::set-title
 (fn [db [_ title]]
   (assoc db ::title title)))

(rf/reg-sub
 ::selected-workflow
 (fn [db _]
   (::selected-workflow db)))

(rf/reg-event-db
 ::set-selected-workflow
 (fn [db [_ workflow]]
   (if workflow
     (assoc db ::selected-workflow ^{:key (:id workflow)} workflow)
     (dissoc db ::selected-workflow))))

(rf/reg-sub
 ::selected-resource
 (fn [db _]
   (::selected-resource db)))

(rf/reg-event-db
 ::set-selected-resource
 (fn [db [_ resource]]
   (if resource
     (assoc db ::selected-resource ^{:key (:id resource)} resource)
     (dissoc db ::selected-resource))))

(rf/reg-sub
 ::selected-form
 (fn [db _]
   (::selected-form db)))

(rf/reg-event-db
 ::set-selected-form
 (fn [db [_ form]]
   (if form
     (assoc db ::selected-form ^{:key (:id form)} form)
     (dissoc db ::selected-form))))

(rf/reg-event-fx
 ::create-catalogue-item
 (fn [db [_ title workflow resource form]]
   (create-catalogue-item title workflow resource form)
   {}))

(rf/reg-event-db
 ::reset-create-catalogue-item
 (fn [db _]
   (dissoc db ::title ::selected-workflow ::selected-resource ::selected-form)))

;;;; UI ;;;;

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [item]
  (let [title @(rf/subscribe [::title])
        workflow @(rf/subscribe [::selected-workflow])
        resource @(rf/subscribe [::selected-resource])
        form @(rf/subscribe [::selected-form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-catalogue-item title workflow resource form])
      :disabled (not (and (not (str/blank? title)) workflow resource form))}
     (text :t.administration/save)]))

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
                  [:input.form-control {:id "title"
                                        :name "title"
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
