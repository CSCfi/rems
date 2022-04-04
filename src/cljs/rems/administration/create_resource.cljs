(ns rems.administration.create-resource
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [organization-field text-field]]
            [rems.administration.duo :refer [duo-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format get-localized-title localized]]
            [rems.util :refer [navigate! post! trim-when-string]]
            [rems.common.util :refer [assoc-some-in build-index]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (dissoc db ::form)
    :dispatch-n (if (-> db :config :enable-duo)
                  [[::licenses] [::duo-codes]]
                  [[::licenses]])}))

(fetcher/reg-fetcher ::licenses "/api/licenses")
(fetcher/reg-fetcher ::duo-codes "/api/resources/duo-codes")

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] (sort-by :id licenses))))

(rf/reg-sub ::duo-form (fn [db _] (get-in db [::form :duo-codes])))
(rf/reg-event-db ::update-duo (fn [db [_ keys value]] (assoc-in db (concat [::form :duo-codes] keys) value)))
(rf/reg-event-db ::set-duo-codes (fn [db [_ duo-codes]]
                                   (->> (for [{:keys [id] :as new-duo} duo-codes]
                                          (or (get (-> db ::form :duo-codes) id)
                                              (update new-duo :restrictions (partial build-index
                                                                                     {:keys [:type]
                                                                                      :value-fn (constantly nil)}))))
                                        (build-index {:keys [:id]})
                                        (assoc-in db [::form :duo-codes]))))

(defn- map-duos-to-request [form]
  (for [duo (vals (:duo-codes form))]
    (-> {:id (:id duo)
         :restrictions (for [restriction (:restrictions duo)]
                         {:type (key restriction)
                          :values (case (key restriction)
                                    :mondo (map #(select-keys % [:id]) (val restriction))
                                    (list {:value (val restriction)}))})}
        (assoc-some-in [:more-info :en] (when (string? (:more-info duo))
                                          (not-empty (str/trim (:more-info duo))))))))

(defn valid-restriction? [restriction]
  (when-let [values (seq (:values restriction))]
    (case (:type restriction)
      :mondo true
      (->> values
           (map :value)
           (every? seq)))))

(defn- valid-request? [request]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:resid request)))
       (->> (get-in request [:resource/duo :duo/codes])
            (mapcat :restrictions)
            (every? valid-restriction?))))

(defn build-request [form]
  (let [request {:organization {:organization/id (get-in form [:organization :organization/id])}
                 :resid (trim-when-string (:resid form))
                 :licenses (map :id (:licenses form))
                 :resource/duo {:duo/codes (map-duos-to-request form)}}]
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

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private licenses-dropdown-id "licenses-dropdown")

(defn- resource-organization-field []
  [organization-field context {:keys [:organization]}])

(defn- resource-id-field []
  [text-field context {:keys [:resid]
                       :label (text :t.create-resource/resid)
                       :placeholder (text :t.create-resource/resid-placeholder)}])

(defn- resource-licenses-field []
  (let [licenses @(rf/subscribe [::licenses])
        selected-licenses @(rf/subscribe [::selected-licenses])
        language @(rf/subscribe [:language])]
    [:div.form-group
     [:label.administration-field-label {:for licenses-dropdown-id} (text :t.create-resource/licenses-selection)]
     [dropdown/dropdown
      {:id licenses-dropdown-id
       :items licenses
       :item-key :id
       :item-label #(str (get-localized-title % language)
                         " (org: "
                         (get-in % [:organization :organization/short-name language])
                         ")")
       :item-selected? #(contains? (set selected-licenses) %)
       :multi? true
       :on-change #(rf/dispatch [::set-licenses %])}]]))

(defn- resource-duos-field []
  (let [selected-duos @(rf/subscribe [::duo-form])]
    [:div.form-group
     [:label.administration-field-label {:for "duos-dropdown"} (text :t.duo/title)]
     [dropdown/dropdown
      {:id "duos-dropdown"
       :items @(rf/subscribe [::duo-codes])
       :item-key :id
       :item-label #(text-format :t.label/dash (:shorthand %) (localized (:label %)))
       :item-selected? #(some? (get selected-duos (:id %)))
       :multi? true
       :on-change #(rf/dispatch [::set-duo-codes %])}]
     (when (not-empty selected-duos)
       [:div.mt-3
        (doall
         (for [duo (sort-by :id (vals selected-duos))]
           ^{:key (:id duo)}
           [duo-field duo {:context {:get-form ::duo-form
                                     :update-form ::update-duo}
                           :create-field? true}]))])]))

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
  (let [loading? @(rf/subscribe [::licenses :fetching?])
        form @(rf/subscribe [::form])
        config @(rf/subscribe [:rems.config/config])]
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
                   (when (:enable-duo config) [resource-duos-field])
                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]])]}]]))

