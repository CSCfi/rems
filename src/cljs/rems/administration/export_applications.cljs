(ns rems.administration.export-applications
  "Page for exporting applications."
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :as atoms]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format localize-time]]
            [rems.util :refer [fetch post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (-> (dissoc db ::form)
            (assoc ::loading? true))
    ::fetch-forms nil}))

(defn- fetch-forms []
  (fetch "/api/forms"
         {:handler #(rf/dispatch [::fetch-forms-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch forms")}))

(rf/reg-fx ::fetch-forms fetch-forms)

(rf/reg-event-db
 ::fetch-forms-result
 (fn [db [_ forms]]
   (-> (assoc db ::forms forms)
       (dissoc ::loading?))))

(rf/reg-sub ::forms (fn [db _] (::forms db)))

(rf/reg-sub ::selected-form-id (fn [db _] (get-in db [::form :form-id])))
(rf/reg-event-db ::set-selected-form-id (fn [db [_ form-id]] (assoc-in db [::form :form-id] form-id)))

(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/applications"
   (text :t.administration/cancel)])

(defn- export-button [form-id]
  ;; Disabling <a> element is done using instructions from Bootstrap docs.
  (let [disabled? (nil? form-id)]
    [(if disabled?
       :a.btn.btn-primary.disabled
       :a.btn.btn-primary)
     {:href (str "/api/applications/export/" form-id)
      :target :_blank
      :aria-disabled disabled?
      :tabIndex (when disabled? "-1")}
     (text :t.administration/export-applications)
     " "
     [atoms/external-link]]))

(def ^:private form-dropdown-id "form-dropdown")

(defn export-applications-form-field []
  (let [forms @(rf/subscribe [::forms])
        selected-form-id @(rf/subscribe [::selected-form-id])
        item-selected? #(= (:form/id %) selected-form-id)]
    [:div.form-group
     [:label {:for form-dropdown-id} (text :t.create-catalogue-item/form-selection)]
     [dropdown/dropdown
      {:id form-dropdown-id
       :items forms
       :item-key :form/id
       :item-label :form/title
       :item-selected? item-selected?
       :on-change #(rf/dispatch [::set-selected-form-id (:form/id %)])}]]))

(defn export-applications-page []
  (let [loading? @(rf/subscribe [::loading?])
        form-id @(rf/subscribe [::selected-form-id])]
    [:div
     [administration-navigator-container]
     [atoms/document-title (text :t.administration/export-applications)]
     [flash-message/component :top]
     [collapsible/component
      {:id "export-applications"
       :title (text :t.administration/export-applications)
       :always [:div
                (if loading?
                  [:div#export-applications-loader [spinner/big]]
                  [:div#export-applications-editor
                   [export-applications-form-field]

                   [:div.col.commands
                    [cancel-button]
                    [export-button form-id]]])]}]]))
