(ns rems.actions.components
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [attachment-link enrich-user textarea]]
            [rems.common.attachment-util :as attachment-util]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text text-format]]
            [rems.util :refer [post!]]))

(defn- action-collapse-id [action-id]
  (str "actions-" action-id))

(defn- action-button-id [action-id]
  (str action-id "-action-button"))

(defn button-wrapper [{:keys [text class] :as props}]
  [:button.btn
   (merge {:type :button
           :class (or class :btn-secondary)}
          (dissoc props :class :text))
   text])

(defn collapse-action-form [id]
  (.collapse (js/$ (str "#" (action-collapse-id id))) "hide"))

(defn cancel-action-button [id]
  [:button.btn.btn-secondary
   {:type :button
    :id (str "cancel-" id)
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click #(.focus (.querySelector js/document (str "#" (action-button-id id))))}
   (text :t.actions/cancel)])

(rf/reg-sub
 ::select-attachments
 (fn [db [_ field-key]]
   (vec (for [kv (get-in db [::select-attachments field-key])
              :when (val kv)]
          {:attachment/id (key kv)}))))
(rf/reg-event-db
 ::set-select-attachments
 (fn [db [_ field-key m]]
   (assoc-in db [::select-attachments field-key] m)))
(rf/reg-sub
 ::get-attachment-selection
 (fn [db [_ field-key id]]
   (get-in db [::select-attachments field-key id] false))) ; if key is not set, default value prevents warnings about uncontrolled input
(rf/reg-event-db
 ::set-attachment-selection
 (fn [db [_ field-key id value]]
   (assoc-in db [::select-attachments field-key id] value)))

(defn select-attachments-field [{:keys [attachments field-key label]}]
  (let [id (str "select-attachments-" field-key)]
    (into
     [:div.select-attachments.form-group {:id id}
      [:label {:for id} label]]
     (for [attachment (sort-by :attachment/id > attachments)
           :let [attachment-id (:attachment/id attachment)]]
       [:div.form-check.mb-2
        [:div.d-flex.align-items-center
         [:input.form-check-input {:type :checkbox
                                   :checked @(rf/subscribe [::get-attachment-selection field-key attachment-id])
                                   :on-change #(let [value (.. % -target -checked)]
                                                 (rf/dispatch [::set-attachment-selection field-key attachment-id value]))}]
         [attachment-link attachment]]
        (when-some [user (get-in attachment [:attachment/user :name])]
          [:b user])]))))

(rf/reg-sub ::comment (fn [db [_ field-key]] (get-in db [::comment field-key])))
(rf/reg-event-db ::set-comment (fn [db [_ field-key value]] (assoc-in db [::comment field-key] value)))

(defn comment-field [{:keys [field-key label]}]
  (let [id (str "comment-" field-key)]
    [:div.form-group
     [:label {:for id} label]
     [textarea {:id id
                :min-rows 4
                :max-rows 4
                :name id
                :value @(rf/subscribe [::comment field-key])
                :on-change (fn [event]
                             (let [value (.. event -target -value)]
                               (rf/dispatch [::set-comment field-key value])))}]]))

(rf/reg-sub ::comment-public (fn [db [_ field-key]] (get-in db [::comment-public field-key] false)))
(rf/reg-event-db ::set-comment-public (fn [db [_ field-key value]] (assoc-in db [::comment-public field-key] value)))

(defn comment-public-field [{:keys [field-key label]}]
  (let [id (str "comment-public-" field-key)]
    [:div.form-group
     [:div.form-check
      [:input.form-check-input {:type "checkbox"
                                :id id
                                :name id
                                :checked @(rf/subscribe [::comment-public field-key])
                                :on-change (fn [event]
                                             (let [checked (.. event -target -checked)]
                                               (rf/dispatch [::set-comment-public field-key checked])))}]
      [:label.form-check-label {:for id}
       label]]]))

(rf/reg-sub ::name (fn [db [_ field-key]] (get-in db [::name field-key])))
(rf/reg-event-db ::set-name (fn [db [_ field-key value]] (assoc-in db [::name field-key] value)))

(rf/reg-sub ::email (fn [db [_ field-key]] (get-in db [::email field-key])))
(rf/reg-event-db ::set-email (fn [db [_ field-key value]] (assoc-in db [::email field-key] value)))

(defn- input-field-view [{:keys [type id label value on-change]}]
  [:div.form-group.field
   [:label {:for id} label]
   [:input.form-control {:type type
                         :id id
                         :value value
                         :on-change #(on-change (.. % -target -value))}]])

(defn name-field [{:keys [field-key]}]
  [input-field-view {:type :text
                     :id (str "name-" field-key)
                     :label (text :t.actions/member-name)
                     :value @(rf/subscribe [::name field-key])
                     :on-change #(rf/dispatch [::set-name field-key %])}])

(defn email-field [{:keys [field-key]}]
  [input-field-view {:type :email
                     :id (str "email-" field-key)
                     :label (text :t.actions/member-email)
                     :value @(rf/subscribe [::email field-key])
                     :on-change #(rf/dispatch [::set-email field-key %])}])

;; attachments in suitable format for api:
(rf/reg-sub ::attachments (fn [db [_ field-key]] (mapv #(select-keys % [:attachment/id]) (get-in db [::attachments field-key]))))
;; attachments with filenames for rendering:
(rf/reg-sub ::attachments-with-filenames (fn [db [_ field-key]] (get-in db [::attachments field-key])))
(rf/reg-event-db ::set-attachments (fn [db [_ field-key value]] (assoc-in db [::attachments field-key] value)))
(rf/reg-event-db ::add-attachment (fn [db [_ field-key value]] (update-in db [::attachments field-key] conj value)))
(rf/reg-event-db
 ::remove-attachment
 (fn [db [_ field-key id]]
   (update-in db [::attachments field-key] (partial remove (comp #{id} :attachment/id)))))

(rf/reg-event-fx
 ::save-attachment
 (fn [_ [_ application-id field-key file]]
   (let [description [text :t.form/upload]]
     (post! "/api/applications/add-attachment"
            {:url-params {:application-id application-id}
             :body file
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [response]
                         (rf/dispatch [::add-attachment field-key {:attachment/id (:id response)
                                                                   :attachment/filename (.. file (get "file") -name)}])))
             :error-handler (fn [response]
                              (if (= 415 (:status response))
                                (flash-message/show-default-error! :actions description
                                                                   [:div
                                                                    [:p [text :t.form/invalid-attachment]]
                                                                    [:p [text-format :t.form/upload-extensions attachment-util/allowed-extensions-string]]])
                                ((flash-message/default-error-handler :actions description) response)))})
     {})))

(defn action-attachment [{:keys [application-id field-key label]}]
  [fields/multi-attachment-view {:id field-key
                                 :label label
                                 :attachments @(rf/subscribe [::attachments-with-filenames field-key])
                                 :on-attach #(rf/dispatch [::save-attachment application-id field-key %])
                                 :on-remove-attachment #(rf/dispatch [::remove-attachment field-key %])}])

(fetcher/reg-fetcher ::reviewers "/api/applications/reviewers" {:result (partial map enrich-user)})
(fetcher/reg-fetcher ::deciders "/api/applications/deciders" {:result (partial map enrich-user)})

(rf/reg-sub ::users (fn [db [_ field-key]] (get-in db [::users field-key])))
(rf/reg-event-db ::set-users (fn [db [_ field-key value]] (assoc-in db [::users field-key] value)))

(defn user-selection [{:keys [subscription field-key]}]
  (let [id (str field-key "-users")]
    [:div.form-group
     [:label {:for id} (text :t.actions/request-selections)]
     [dropdown/dropdown
      {:id id
       :items @(rf/subscribe subscription)
       :item-key :userid
       :item-label :display
       :item-selected? (set @(rf/subscribe [::users field-key]))
       :multi? true
       :on-change #(rf/dispatch [::set-users field-key %])}]]))

(defn action-form-view
  "Renders an action form that is collapsible.

  `id` - the id of the form
  `title` - the name of the action
  `content` - content of the form
  `buttons` - the actions that can be executed
  `:collapse-id` - optionally the collapse group the action is part of"
  [id title buttons content & [{:keys [collapse-id]}]]
  [:div.collapse {:id (action-collapse-id id)
                  :data-parent (if collapse-id (str "#" collapse-id) "#actions-forms")
                  :tab-index "-1"
                  :ref (fn [elem]
                         (when elem
                           (.on (js/$ elem) "shown.bs.collapse" #(.focus elem))))}
   [:h3.mt-3 title]
   content
   (into [:div.col.commands [cancel-action-button id]] buttons)])

(defn action-button [{:keys [id text class on-click]}]
  [:button.btn
   {:id (action-button-id id)
    :class (str (or class "btn-secondary")
                " btn-opens-more")
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click on-click}
   text])

(defn action-link [{:keys [id text on-click]}]
  [:a.dropdown-item.btn.btn-link
   {:id (action-button-id id)
    :href "#"
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click on-click}
   text])

(defn command! [command params {:keys [description collapse on-finished]}]
  (assert (qualified-keyword? command)
          (pr-str command))
  (post! (str "/api/applications/" (name command))
         {:params params
          :handler (flash-message/default-success-handler
                    :actions
                    description
                    (fn [_]
                      (collapse-action-form collapse)
                      (on-finished)))
          :error-handler (flash-message/default-error-handler :actions description)}))

(defn guide []
  [:div])
