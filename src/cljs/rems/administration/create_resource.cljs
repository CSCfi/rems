(ns rems.administration.create-resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [navigate! fetch post! trim-when-string]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (dissoc db ::form)
    :dispatch-n [[:rems.administration.administration/owned-organizations {:owner (get-in db [:identity :user :userid])}]
                 [::licenses {:active true}]]}))

(fetcher/reg-fetcher ::licenses "/api/licenses")

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] (sort-by :id licenses))))

;; form submit

(defn- valid-request? [form request]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:resid request)))))

(defn build-request [form]
  (let [request {:organization (get-in form [:organization :organization/id])
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

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private licenses-dropdown-id "licenses-dropdown")

(rf/reg-sub
 ::selected-organization
 (fn [db _]
   (let [organization-id (get-in db [::form :form/organization])]
     (get-in db [:organizations-by-id organization-id]))))

(rf/reg-event-db ::set-selected-organization (fn [db [_ organization]] (assoc-in db [::form :data :form/organization] organization)))

(defn- resource-organization-field []
  [fields/organization-field {:id "organization-dropdown"
                              :value @(rf/subscribe [::selected-organization])
                              :on-change #(rf/dispatch [::set-selected-organization %])}])

(defn- resource-id-field []
  [text-field context {:keys [:resid]
                       :label (text :t.create-resource/resid)
                       :placeholder (text :t.create-resource/resid-placeholder)}])

(defn- resource-licenses-field []
  (let [licenses @(rf/subscribe [::licenses])
        selected-licenses @(rf/subscribe [::selected-licenses])
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for licenses-dropdown-id} (text :t.create-resource/licenses-selection)]
     [dropdown/dropdown
      {:id licenses-dropdown-id
       :items licenses
       :item-key :id
       :item-label #(str (get-localized-title % language)
                         " (org: "
                         (:organization %)
                         ")")
       :item-selected? #(contains? (set selected-licenses) %)
       :multi? true
       :on-change #(rf/dispatch [::set-licenses %])}]]))

(defn- save-resource-button [form]
  (let [request (build-request form)]
    [:button#save.btn.btn-primary
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
  (let [loading? (or @(rf/subscribe [:rems.administration.administration/owned-organizations :fetching?])
                     @(rf/subscribe [::licenses :fetching?]))
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
                  [:div#resource-editor.fields
                   [resource-organization-field]
                   [resource-id-field]
                   [resource-licenses-field]

                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]])]}]]))
