(ns rems.administration.create-resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [navigate! fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db
               ::form {:licenses #{}}
               ::loading? true)
    ::fetch-licenses nil}))

(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] (sort-by :id licenses))))

;; form submit

(defn- valid-request? [request]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:resid request)))))

(defn build-request [form]
  (let [request {:organization (:organization form)
                 :resid (:resid form)
                 :licenses (map :id (:licenses form))}]
    (when (valid-request? request)
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

(def ^:private licenses-dropdown-id "licenses-dropdown")

(defn- resource-organization-field []
  [text-field context {:keys [:organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- resource-id-field []
  [text-field context {:keys [:resid]
                       :label (text :t.create-resource/resid)
                       :placeholder (text :t.create-resource/resid-placeholder)}])

(defn- resource-licenses-field []
  (let [available-licenses @(rf/subscribe [::licenses])
        selected-licenses @(rf/subscribe [::selected-licenses])
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label {:for licenses-dropdown-id} (text :t.create-resource/licenses-selection)]
     [dropdown/dropdown
      {:id licenses-dropdown-id
       :items available-licenses
       :item-key :id
       :item-label #(get-localized-title % language)
       :item-selected? #(contains? (set selected-licenses) %)
       :multi? true
       :on-change #(rf/dispatch [::set-licenses %])}]]))

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
     [administration-navigator-container]
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
