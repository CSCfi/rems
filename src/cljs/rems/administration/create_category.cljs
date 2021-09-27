(ns rems.administration.create-category
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [checkbox localized-text-field organization-field radio-button-group text-field text-field-inline]]
            ;; [rems.administration.components :refer [organization-field text-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            ;; [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            ;; [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [navigate! post! put! trim-when-string fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ id]]
   {:db (assoc db
               ::form {:title {:localizations {:en "En"
                                               :fi "FI"
                                               :sv "SV"}}}
               ::editing? (some? id)
               ::id id)
    :dispatch-n [(when id [::fetch-category id])]}))

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-sub ::editing? (fn [db _] (::editing? db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::category (fn [db _] (::category db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))
(rf/reg-sub ::id (fn [db _] (::id db)))

(rf/reg-event-fx
 ::fetch-category-result
 (fn [{:keys [db]} [_ category]]
   {:db (-> db
            (assoc ::category category)
            (dissoc ::loading?)
            (assoc ::form {;;  (:title 
                          ;;          (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)
                          ;;                 )
                           :organization (:organization category)}))
    ;; :dispatch-n [::fetch-category-success]
    }))


(rf/reg-event-db
 ::fetch-category-success
 (fn [db [_ {:keys [data organization]}]]
   (update db ::form merge {:title {:localizations {:en (:en (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))
                                                    :fi (:fi (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))
                                                    :sv (:sv (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))}}
                            :organization organization})))
(rf/reg-event-fx
 ::fetch-category
 (fn [{:keys [db]} [_ category-id]]
   (fetch (str "/api/categories/" category-id)
          {:handler (fn [response]
                      (rf/dispatch [::fetch-category-success response]))
           :error-handler (flash-message/default-error-handler :top "Fetch category")})
   {:db (assoc db ::loading? true)}))

;; form submit

(defn- valid-request? [form request]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:resid request)))))

(defn build-request [form]
  ;; (let [request {:id 89
  ;;                :organization "text"}]
  ;;   (when (valid-request? form request)
  ;;     request))
  {:id 800
   :organization "text"})

(rf/reg-event-fx
 ::create-category
 (fn [_ [_ request]]
   (let [description [text :t.administration/save]]
     (post! "/api/categories/create"
            {:params request
             ;; TODO: render the catalogue items that use this resource in the error handler
             :handler (flash-message/default-success-handler
                        :top description #(navigate! (str "/administration/categories/" (:id %))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::edit-category
 (fn [_ [_ request]]
   (let [description "Edit category"]
     (put! "/api/categories/edit"
           {:params request
            :handler (flash-message/default-success-handler
                       :top description #(navigate! (str "/administration/categories/" (:id request))))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(defn- category-organization-field []
  [organization-field context {:keys [:organization]}])

(defn- category-id-field []
  [text-field context {:keys [:id]
                       :label (text :t.create-resource/resid)
                       :placeholder (text :t.create-resource/resid-placeholder)}])

(defn- category-name-field [language]
  [text-field context {:keys [:title :localizations language]
                       :label (str "Category name " language)}])

(defn- save-resource-button [form]
  (let [request {;;  :id (js/parseInt (trim-when-string (:id form)))
                 :data {:title (:localizations (:title form))}
                 :organization {:organization/id (get-in form [:organization :organization/id])}
                ;;  (trim-when-string (:en (:organization/short-name (:organization form))))
                 ;; (trim-when-string (get-in (:organization form) [:organization/id]))
                 }
        id @(rf/subscribe [::id])]
    [:button#save.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (if id
                    (rf/dispatch [::edit-category request])
                    (rf/dispatch [::create-category request])))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/resources"
   (text :t.administration/cancel)])

(defn create-category-page []
  (let [languages @(rf/subscribe [:languages])
        editing? @(rf/subscribe [::editing?])
        ;; loading? @(rf/subscribe [::licenses :fetching?])
        form @(rf/subscribe [::form])]
    [:div
     [administration/navigator]
     [document-title "Create category"]
     [flash-message/component :top]
     (if-not editing?
       [collapsible/component
        {:id "create-resource"
         :title "Create category"
         :always [:div
                  [:div#resource-editor.fields
                   (for [language languages]
                     [category-name-field language])
                   [category-organization-field]
                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]]]}]

       [collapsible/component
        {:id "create-resource"
         :title "Edit category"
         :always [:div
                  [:div#resource-editor.fields
                   (for [language languages]
                     [category-name-field language])
                   [category-organization-field]
                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]]]}])]))