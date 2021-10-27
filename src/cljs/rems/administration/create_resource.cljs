(ns rems.administration.create-resource
  (:require [goog.functions :refer [debounce]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [medley.core :refer [assoc-some remove-vals]]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [organization-field text-field date-field input-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title localized]]
            [rems.util :refer [navigate! post! trim-when-string]]
            [rems.common.duo :refer [duo-restriction-label]]
            [rems.common.util :refer [assoc-some-in]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (dissoc db ::form)
    :dispatch-n [[::licenses] [::duo-codes]]}))

(fetcher/reg-fetcher ::licenses "/api/licenses")
(fetcher/reg-fetcher ::duo-codes "/api/resources/duo-codes")
(fetcher/reg-fetcher ::mondo-codes "/api/resources/search-mondo-codes")

;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] (sort-by :id licenses))))

(rf/reg-sub ::selected-duo-codes (fn [db _] (get-in db [::form :duo-codes])))
(rf/reg-event-db ::set-duo-codes (fn [db [_ duo-codes]] (assoc-in db [::form :duo-codes] (sort-by :id duo-codes))))

(rf/reg-sub ::duo-restrictions (fn [db [_ duo]] (get-in db [::form :duo-restrictions duo])))
(rf/reg-event-db ::set-duo-restrictions (fn [db [_ path restrictions]] (assoc-in db (into [::form :duo-restrictions] (flatten [path])) restrictions)))

;; form submit

(defn- map-duos-to-request [{:keys [duo-codes duo-restrictions]}]
  (when (seq duo-codes)
    (let [join-duo-restrictions (fn [duo-id]
                                  (->> (get duo-restrictions duo-id)
                                       (remove-vals #(or (str/blank? %) (empty? %)))
                                       (mapv (fn [[type value]]
                                               (-> {:type type}
                                                   (assoc :values (case type
                                                                    :mondo (mapv #(select-keys % [:id]) value)
                                                                    [{:value value}])))))
                                       seq))]
      (mapv (fn [{:keys [id]}]
              (-> {:id id}
                  (assoc-some :restrictions (join-duo-restrictions id))))
            duo-codes))))

(defn- valid-request? [request]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:resid request)))))

(defn build-request [form]
  (let [request (-> {:organization {:organization/id (get-in form [:organization :organization/id])}
                     :resid (trim-when-string (:resid form))
                     :licenses (map :id (:licenses form))}
                    (assoc-some-in [:resource/duo :duo/codes] (map-duos-to-request form)))]
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

(defn- duo-restriction [{:keys [type]} duo-id]
  (let [duo-restrictions @(rf/subscribe [::duo-restrictions duo-id])
        restriction-label (text (duo-restriction-label type))]
    (case type
      :mondo
      (let [fetching? @(rf/subscribe [::mondo-codes :fetching?])
            fetch-mondo-codes (debounce (fn [{:keys [query-string on-data]}]
                                          (rf/dispatch [::mondo-codes {:search-text query-string} {:on-data on-data}]))
                                        200)
            update-path [duo-id :mondo]]
        [:<>
         [:label.administration-field-label {:for "mondos-dropdown"} restriction-label]
         [dropdown/async-dropdown
          {:id "mondos-dropdown"
           :item-key :id
           :item-label #(str (:id %) " – " (:label %))
           :multi? true
           :on-change #(rf/dispatch [::set-duo-restrictions update-path %])
           :on-load-options fetch-mondo-codes
           :loading? fetching?}]
         (when-let [mondos (seq (:mondo duo-restrictions))]
           [:div.mt-3
            (doall
             (for [{:keys [id label]} mondos]
               ^{:key id}
               [:div.form-field
                [:p.administration-field-label id]
                [:p label]]))])])

      :date
      (let [update-path [:duo-restrictions duo-id :date]]
        [date-field context
         {:label restriction-label
          :keys update-path}])

      :months
      (let [update-path [:duo-restrictions duo-id :months]]
        [input-field {:type :number
                      :context context
                      :keys update-path
                      :label restriction-label
                      :input-style {:max-width 200}}])

      (:topic :location :institute :collaboration :project :users)
      (let [update-path [:duo-restrictions duo-id type]]
        [text-field context
         {:keys update-path
          :label restriction-label}])

      nil)))

(defn- duo-field [duo]
  [:<>
   [:p
    [:span.administration-field-label (:shorthand duo)]
    [:span (str " – " (localized (:label duo)))]]
   [:p (localized (:description duo))]
   [:p (str "(" (:id duo) ")")]
   (into [:<>]
         (for [restriction (:restrictions duo)]
           [duo-restriction restriction (:id duo)]))])

(defn- resource-duos-field []
  (let [duos @(rf/subscribe [::duo-codes])
        selected-duos @(rf/subscribe [::selected-duo-codes])]
    [:div.form-group
     [:label.administration-field-label {:for "duos-dropdown"}
      (text :t.create-resource.duos/select-duo-codes)]
     [dropdown/dropdown
      {:id "duos-dropdown"
       :items duos
       :item-key :id
       :item-label #(let [shorthand (:shorthand %)
                          label (localized (:label %))]
                      (if (seq shorthand)
                        (str shorthand " – " label)
                        (str (:id %) " – " label)))
       :item-selected? #(contains? (set selected-duos) %)
       :multi? true
       :on-change #(rf/dispatch [::set-duo-codes %])}]
     (when (seq selected-duos)
       [:div {:style {:margin-top "1rem"}}
        (doall
         (for [duo selected-duos]
           ^{:key (:id duo)}
           [:div.form-field
            [duo-field duo]]))])]))

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
                   (when (:enable-duo config)
                     [resource-duos-field])

                   [:div.col.commands
                    [cancel-button]
                    [save-resource-button form]]])]}]]))
