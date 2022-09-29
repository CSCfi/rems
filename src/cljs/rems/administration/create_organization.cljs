(ns rems.administration.create-organization
  (:require [clojure.string :as str]
            [medley.core :refer [indexed map-vals remove-nth]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field text-field]]
            [rems.administration.items :as items]
            [rems.atoms :as atoms :refer [enrich-user document-title]]
            [rems.common.util :refer [+email-regex+ conj-vec]]
            [rems.collapsible :as collapsible]
            [rems.config :as config]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [navigate! post! put! trim-when-string]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ organization-id]]
   {:db (assoc db
               ::organization-id organization-id
               ::available-owners nil
               ::editing? (some? organization-id)
               ::form {:type :organization/default})
    :dispatch-n [[::available-owners]
                 (when organization-id [::organization])]}))

(rf/reg-sub ::organization-id (fn [db _] (::organization-id db)))
(rf/reg-sub ::editing? (fn [db _] (::editing? db)))

;;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db
 ::fetch-organization-success
 (fn [db [_ organization]]
   (update db ::form merge organization)))

(fetcher/reg-fetcher ::organization "/api/organizations/:id" {:path-params (fn [db] {:id (::organization-id db)})
                                                              :on-success #(rf/dispatch [::fetch-organization-success %])})

;;; form submit

(defn- valid-review-email? [languages review-email]
  (and (when (map? (:name review-email))
         (not-any? str/blank? (vals (:name review-email))))
       (= (set languages)
          (set (keys (:name review-email))))
       (not (str/blank? (:email review-email)))
       (re-matches +email-regex+ (:email review-email))))

(defn- valid-owner? [owner]
  (not (str/blank? owner)))

(defn- valid-create-request? [request languages]
  (and
   (not (str/blank? (:organization/id request)))
   (= (set languages)
      (set (keys (:organization/short-name request)))
      (set (keys (:organization/name request))))
   (not-any? str/blank? (vals (:organization/short-name request)))
   (not-any? str/blank? (vals (:organization/name request)))
   (every? valid-owner? (:organization/owners request))
   (every? (partial valid-review-email? languages) (:organization/review-emails request))))

(defn- build-review-email [review-email]
  {:name (when (map? (:name review-email))
           (map-vals trim-when-string (:name review-email)))
   :email (trim-when-string (:email review-email))})

(defn build-create-request [form languages]
  (let [request {:organization/id (trim-when-string (:organization/id form))
                 :organization/short-name (map-vals trim-when-string (:organization/short-name form))
                 :organization/name (map-vals trim-when-string (:organization/name form))
                 :organization/owners (mapv #(select-keys % [:userid]) (:organization/owners form))
                 :organization/review-emails (mapv build-review-email (:organization/review-emails form))}]
    (when (valid-create-request? request languages)
      request)))

(defn- valid-edit-request? [request languages]
  (and (= (set languages)
          (set (keys (:organization/short-name request)))
          (set (keys (:organization/name request))))
       (not-any? str/blank? (vals (:organization/short-name request)))
       (not-any? str/blank? (vals (:organization/name request)))
       (every? valid-owner? (:organization/owners request))
       (every? (partial valid-review-email? languages) (:organization/review-emails request))))

(defn build-edit-request [id form languages]
  (let [request {:organization/id (:organization/id form)
                 :organization/short-name (map-vals trim-when-string (:organization/short-name form))
                 :organization/name (map-vals trim-when-string (:organization/name form))
                 :organization/owners (mapv #(select-keys % [:userid]) (:organization/owners form))
                 :organization/review-emails (mapv build-review-email (:organization/review-emails form))}]
    (when (valid-edit-request? request languages)
      request)))

(rf/reg-event-fx
 ::create-organization
 (fn [_ [_ request]]
   (let [description [text :t.administration/create-organization]]
     (post! "/api/organizations/create"
            {:params request
             :handler (flash-message/default-success-handler
                       :top description #(do (config/fetch-organizations!)
                                             (navigate! (str "/administration/organizations/" (:organization/id %)))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-fx
 ::edit-organization
 (fn [_ [_ request]]
   (let [description [text :t.administration/edit-organization]]
     (put! "/api/organizations/edit"
           {:params request
            :handler (flash-message/default-success-handler
                      :top description #(do (config/fetch-organizations!)
                                            (navigate! (str "/administration/organizations/" (:organization/id %)))))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-db ::set-owners (fn [db [_ owners]] (assoc-in db [::form :organization/owners] (sort-by :userid owners))))

(fetcher/reg-fetcher ::available-owners "/api/organizations/available-owners" {:result (partial mapv enrich-user)})



 ;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private owners-dropdown-id "owners-dropdown")

(defn- organization-id-field []
  [text-field context {:keys [:organization/id]
                       :label (text :t.administration/id)
                       :readonly @(rf/subscribe [::editing?])}])

(defn- organization-short-name-field []
  [localized-text-field context {:keys [:organization/short-name]
                                 :label (text :t.administration/short-name)}])

(defn- organization-name-field []
  [localized-text-field context {:keys [:organization/name]
                                 :label (text :t.administration/title)}])

(defn- save-organization-button []
  (let [form @(rf/subscribe [::form])
        id @(rf/subscribe [::organization-id])
        languages @(rf/subscribe [:languages])
        request (if id
                  (build-edit-request id form languages)
                  (build-create-request form languages))]
    [:button.btn.btn-primary
     {:type :button
      :id :save
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (if id
                    (rf/dispatch [::edit-organization request])
                    (rf/dispatch [::create-organization request])))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   (if @(rf/subscribe [::editing?])
     (str "/administration/organizations/" @(rf/subscribe [::organization-id]))
     "/administration/organizations")
   (text :t.administration/cancel)])

(defn- organization-owners-field []
  (let [form @(rf/subscribe [::form])
        all-owners @(rf/subscribe [::available-owners])
        selected-owners (set (map :userid (get-in form [:organization/owners])))
        organization-id (:organization/id form)
        org-owner? (->> @(rf/subscribe [:owned-organizations])
                        (some (comp #{organization-id} :organization/id)))]
    [:div.form-group
     [:label.administration-field-label
      {:for owners-dropdown-id}
      (str (text :t.administration/owners) " " (text :t.administration/optional))]
     [dropdown/dropdown
      {:id owners-dropdown-id
       :items all-owners
       :item-key :userid
       :item-label :display
       :item-selected? #(contains? selected-owners (% :userid))
       :multi? true
       :disabled? (and @(rf/subscribe [::editing?])
                       (not org-owner?))
       :on-change #(rf/dispatch [::set-owners %])}]]))

(defn- remove-review-email-button [field-index]
  [items/remove-button #(rf/dispatch [::remove-review-email field-index])])

(rf/reg-event-db
 ::add-review-email
 (fn [db [_]]
   (update-in db [::form :organization/review-emails] conj-vec {})))


(rf/reg-event-db
 ::remove-review-email
 (fn [db [_ field-index]]
   (update-in db
              [::form :organization/review-emails]
              (fn [review-emails]
                (vec (remove-nth field-index review-emails))))))


(defn- organization-review-email-field [field-index]
  [:div.form-field
   [:div.form-field-header
    [:h4 (text-format :t.administration/review-email-n (inc field-index))]
    [:div.form-field-controls [remove-review-email-button field-index]]]
   [:div.row
    [:div.col-md
     [localized-text-field context {:keys [:organization/review-emails field-index :name]
                                    :label (text :t.administration/name)}]]
    [:div.col-md
     [text-field context {:keys [:organization/review-emails field-index :email]
                          :label (text :t.administration/email)}]]]])

(defn- organization-review-emails-field []
  (let [form @(rf/subscribe [::form])]
    [:div.form-group
     [:label.administration-field-label
      {:for :review-emails}
      (str (text :t.administration/review-emails) " " (text :t.administration/optional))]
     (for [[field-index _review-email] (indexed (:organization/review-emails form))]
       ^{:key field-index} [organization-review-email-field field-index])
     [:div.dashed-group.text-center
      [:a#add-review-email {:href "#"
                            :on-click (fn [event]
                                        (.preventDefault event)
                                        (rf/dispatch [::add-review-email]))}
       (text :t.administration/add)]]]))

(defn create-organization-page []
  (let [loading? (or @(rf/subscribe [::available-owners :fetching?])
                     @(rf/subscribe [::organization :fetching?]))
        editing? @(rf/subscribe [::editing?])
        title (if editing?
                (text :t.administration/edit-organization)
                (text :t.administration/create-organization))]
    [:div
     [administration/navigator]
     [document-title title]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-organization"
       :title title
       :always (if loading?
                 [:div#organization-loader [spinner/big]]
                 [:div#organization-editor.fields
                  [organization-id-field]
                  [organization-short-name-field]
                  [organization-name-field]
                  [organization-owners-field]
                  [organization-review-emails-field]
                  [:div.col.commands
                   [cancel-button]
                   [save-organization-button]]])}]]))
