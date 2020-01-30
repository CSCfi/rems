(ns rems.administration.create-resource
  (:require [clojure.string :as str]
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
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [navigate! fetch post! trim-when-string]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   (let [roles (get-in db [:identity :roles])
         organization (get-in db [:identity :user :organization])]
     {:db (assoc db
                 ::form (merge {:licenses []}
                               (when (roles/disallow-setting-organization? roles)
                                 {:organization organization}))
                 ::loading? true)
      ::fetch-licenses nil})))

(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-organization (fn [db _] (get-in db [::form :organization])))
(rf/reg-event-db ::set-selected-organization (fn [db [_ organization]] (assoc-in db [::form :organization] organization)))

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] (sort-by :id licenses))))

;; form submit

(defn- valid-request? [form request]
  (and (every? #(= (:organization request) %) (map :organization (:licenses form)))
       (not (str/blank? (:organization request)))
       (not (str/blank? (:resid request)))))

(defn build-request [form]
  (let [request {:organization (:organization form)
                 :resid (trim-when-string (:resid form))
                 :licenses (map :id (:licenses form))}]
    (when (valid-request? form request)
      request)))

(rf/reg-event-fx
 ::create-resource
 (fn [_ [_ request]]
   (let [description [text :t.administration/save]]
     (post! "/api/resources/create"
            {:params request
             ;; TODO: render the catalogue items that use this resource in the error handler
             :handler (flash-message/default-success-handler
                       :top description #(navigate! (str "/administration/resources/" (:id %))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

;; available licenses

(defn- fetch-licenses []
  (fetch "/api/licenses"
         {:url-params {:active true}
          :handler #(rf/dispatch [::fetch-licenses-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch licenses")}))

(rf/reg-fx ::fetch-licenses (fn [_] (fetch-licenses)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))


;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private organization-dropdown-id "organization-dropdown")
(def ^:private licenses-dropdown-id "licenses-dropdown")

(defn- list-organizations []
  (let [licenses @(rf/subscribe [::licenses])]
    (sort (distinct (mapv :organization licenses)))))

(defn- resource-organization-field []
  (let [organizations (list-organizations)
        selected-organization @(rf/subscribe [::selected-organization])
        item-selected? #(= % selected-organization)
        readonly (roles/disallow-setting-organization? (:roles @(rf/subscribe [:identity])))]
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
         :on-change #(do (rf/dispatch [::set-selected-organization %])
                         (rf/dispatch [::set-licenses []]))}])]))

(defn- resource-id-field []
  [text-field context {:keys [:resid]
                       :label (text :t.create-resource/resid)
                       :placeholder (text :t.create-resource/resid-placeholder)}])

(defn- resource-licenses-field []
  (let [organization @(rf/subscribe [::selected-organization])
        licenses @(rf/subscribe [::licenses])
        compatible-licenses (filter #(= organization (% :organization)) licenses)
        selected-licenses @(rf/subscribe [::selected-licenses])
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for licenses-dropdown-id} (text :t.create-resource/licenses-selection)]
     (cond
       (nil? organization)
       [fields/readonly-field {:id licenses-dropdown-id
                               :value (text :t.administration/select-organization)}]

       :else
       [dropdown/dropdown
        {:id licenses-dropdown-id
         :items compatible-licenses
         :item-key :id
         :item-label #(get-localized-title % language)
         :item-selected? #(contains? (set selected-licenses) %)
         :multi? true
         :on-change #(rf/dispatch [::set-licenses %])}])]))

(defn- save-resource-button [form]
  (let [request (build-request form)]
    [:button.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (rf/dispatch [::create-resource request]))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/resources"
   (text :t.administration/cancel)])

(defn create-resource-page []
  (let [loading? @(rf/subscribe [::loading?])
        form @(rf/subscribe [::form])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/create-resource)]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-resource"
       :title (text :t.administration/create-resource)
       :always [:div
                (if loading?
                  [:div#resource-loader [spinner/big]]
                  [:div#resource-editor
                   [resource-organization-field]
                   [resource-id-field]
                   [resource-licenses-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]])]}]]))
