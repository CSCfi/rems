(ns rems.administration.create-resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [text-field]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text localize-item]]
            [rems.util :refer [dispatch! fetch post!]]
            [rems.status-modal :refer [status-modal]]))

(defn- reset-form [db]
  (assoc db
         ::form {:licenses #{}}
         ::loading? true))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (reset-form db)
    ::fetch-licenses nil}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

;; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub
 ::selected-licenses
 (fn [db _]
   (get-in db [::form :licenses])))

(rf/reg-event-db
 ::select-license
 (fn [db [_ license]]
   (update-in db [::form :licenses] conj license)))

(rf/reg-event-db
 ::deselect-license
 (fn [db [_ license]]
   (update-in db [::form :licenses] disj license)))


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

(defn- create-resource [request on-success on-error]
  (post! "/api/resources/create"
         {:params request
          :handler (fn [response]
                     (if (:success response)
                       (on-success response)
                       (on-error (first (:errors response)))))
          :error-handler on-error}))

(rf/reg-event-fx
 ::create-resource
 (fn [_ [_ {:keys [request on-pending on-success on-error]}]]
   (create-resource request on-success on-error)
   (on-pending)
   {}))
                                        ;
;; available licenses

(defn- fetch-licenses []
  (fetch "/api/licenses?active=true"
         {:handler #(rf/dispatch [::fetch-licenses-result %])}))

(rf/reg-fx
 ::fetch-licenses
 (fn [_]
   (fetch-licenses)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub
 ::licenses
 (fn [db _]
   (::licenses db)))


;;;; UI

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

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
        selected-licenses @(rf/subscribe [::selected-licenses])]
    [:div.form-group
     [:label (text :t.create-resource/licenses-selection)]
     [autocomplete/component
      {:value (->> selected-licenses
                   (map localize-item)
                   (sort-by :id))
       :items (map localize-item available-licenses)
       :value->text #(:title %2)
       :item->key :id
       :item->text :title
       :item->value identity
       :search-fields [:title]
       :add-fn #(rf/dispatch [::select-license %])
       :remove-fn #(rf/dispatch [::deselect-license %])}]]))

(defn- save-resource-button [{:keys [form on-success on-pending on-error]}]
  (let [request (build-request form)]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-resource {:request request
                                                  :on-success on-success
                                                  :on-pending on-pending
                                                  :on-error on-error}])
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/resources")}
   (text :t.administration/cancel)])

(defn create-resource-page []
  (let [loading? (rf/subscribe [::loading?])
        form (rf/subscribe [::form])
        state (r/atom nil)
        on-success #(reset! state {:status :saved})
        on-pending #(reset! state {:status :pending})
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (when (= :saved (:status @state))
                              (dispatch! "#/administration/resources"))
                            (reset! state nil))]
    (fn []
      [:div
       [administration-navigator-container]
       [:h2 (text :t.administration/create-resource)]
       [collapsible/component
        {:id "create-resource"
         :title (text :t.administration/create-resource)
         :always [:div
                  (when (:status @state)
                    [status-modal (assoc @state
                                         :description (text :t.administration/save)
                                         :on-close on-modal-close)])
                  (if @loading?
                    [:div#resource-loader [spinner/big]]
                    [:div#resource-editor
                     [resource-organization-field]
                     [resource-id-field]
                     [resource-licenses-field]

                     [:div.col.commands
                      [cancel-button]
                      [save-resource-button {:form @form
                                             :on-success on-success
                                             :on-pending on-pending
                                             :on-error on-error}]]])]}]])))
