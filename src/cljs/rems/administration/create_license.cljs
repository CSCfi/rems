(ns rems.administration.create-license
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field localized-textarea-autosize organization-field perform-action-button radio-button-group]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.attachment]
            [rems.collapsible :as collapsible]
            [rems.config]
            [rems.globals]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [navigate! post! trim-when-string]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::form)}))

(rf/reg-sub ::form (fn [db _] (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub
 ::attachment
 :<- [::form]
 (fn [form [_ language]]
   (get-in form [:localizations language])))

(def license-type-link "link")
(def license-type-text "text")
(def license-type-attachment "attachment")

(defn parse-textcontent [form license-type]
  (condp = license-type
    license-type-link (trim-when-string (:link form))
    license-type-text (:text form)
    license-type-attachment (:attachment-filename form)
    nil))

(defn- build-localization [data license-type]
  {:title (trim-when-string (:title data))
   :textcontent (parse-textcontent data license-type)
   :attachment-id (:attachment-id data)})

(defn- valid-localization? [request data]
  (case (:licensetype request)
    license-type-attachment (and (not (str/blank? (:title data)))
                                 (not (str/blank? (:textcontent data)))
                                 (some? (:attachment-id data)))
    ; else
    (and (not (str/blank? (:title data)))
         (not (str/blank? (:textcontent data))))))

(defn- valid-request? [request languages]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:licensetype request)))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? #(valid-localization? request %) (vals (:localizations request)))))

(defn build-request [form languages]
  (let [license-type (:licensetype form)
        request {:licensetype license-type
                 :organization {:organization/id (get-in form [:organization :organization/id])}
                 :localizations (into {} (map (fn [[lang data]]
                                                [lang (build-localization data license-type)])
                                              (:localizations form)))}]
    (when (valid-request? request languages) request)))

(rf/reg-event-fx
 ::create-license
 (fn [_ [_ request]]
   (let [description [text :t.administration/create-license]]
     (post! "/api/licenses/create"
            {:params request
             :handler (flash-message/default-success-handler
                       :top description #(navigate! (str "/administration/licenses/" (:id %))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-event-db
 ::clear-attachment-errors
 (fn [db _]
   (update-in db
              [::form :localizations]
              (partial map-vals (fn [language]
                                  (if (= :error (:attachment-upload-status language))
                                    (dissoc language :attachment-upload-status)
                                    language))))))

(defn- clear-attachment-errors! []
  (rf/dispatch [::clear-attachment-errors])
  (flash-message/clear-message! :top))

(rf/reg-event-db
 ::attachment-saved
 (fn [db [_ language attachment-id]]
   (-> db
       (assoc-in [::form :localizations language :attachment-id] attachment-id)
       ;; filename set earlier already
       (assoc-in [::form :localizations language :attachment-upload-status] :success))))

(defn- save-attachment! [{:keys [language filename form-data]}]
  (clear-attachment-errors!)
  (rf/dispatch [::set-form-field [:localizations language :attachment-filename] filename])
  (rf/dispatch [::set-form-field [:localizations language :attachment-upload-status] :pending])
  (post! "/api/licenses/add_attachment"
         {:body form-data
          :handler (fn [response]
                     (rf/dispatch [::attachment-saved language (:id response)]))
          :error-handler (fn [response]
                           (rf/dispatch [::set-form-field [:localizations language :attachment-filename] nil])
                           (rf/dispatch [::set-form-field [:localizations language :attachment-upload-status] :error])
                           ((flash-message/default-error-handler :top "Save attachment") response))}))

(rf/reg-event-db
 ::attachment-removed
 (fn [db [_ language]]
   (-> db
       (assoc-in [::form :localizations language :attachment-id] nil)
       (assoc-in [::form :localizations language :attachment-filename] nil)
       (assoc-in [::form :localizations language :attachment-upload-status] nil))))

(defn- remove-attachment! [{:keys [attachment-id language]}]
  (clear-attachment-errors!)
  (post! "/api/licenses/remove_attachment"
         {:url-params {:attachment-id attachment-id}
          :body {}
          :handler #(rf/dispatch [::attachment-removed language])
          :error-handler (flash-message/default-error-handler :top "Remove attachment")}))


;;;; UI

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- license-organization-field []
  [organization-field context {:keys [:organization]}])

(defn- license-title-field []
  [localized-text-field context {:localizations-key :title
                                 :label (text :t.create-license/title)}])

(defn- license-type-radio-group []
  [radio-button-group context {:id :license-type
                               :keys [:licensetype]
                               :label (text :t.create-license/license-type)
                               :orientation :horizontal
                               :options [{:value license-type-link
                                          :label (text :t.create-license/external-link)}
                                         {:value license-type-text
                                          :label (text :t.create-license/inline-text)}
                                         {:value license-type-attachment
                                          :label (text :t.create-license/license-attachment)}]}])

(defn- license-link-field []
  [localized-text-field context {:localizations-key :link
                                 :label (text :t.create-license/link-to-license)
                                 :placeholder "https://example.com/license"}])

(defn- license-text-field []
  [localized-textarea-autosize context {:localizations-key :text
                                        :label (text :t.create-license/license-text)}])

(defn- upload-attachment-action [language]
  (let [attachment @(rf/subscribe [::attachment language])]
    {:id (str "upload-license-button-" (name language))
     :hide-info? true
     :filename (:attachment-filename attachment)
     :status (:attachment-upload-status attachment)
     :on-upload (fn [{:keys [filecontent form-data]}]
                  (save-attachment! {:language language
                                     :form-data form-data
                                     :filename (.-name filecontent)}))}))

(defn- preview-attachment-action [language]
  (let [attachment @(rf/subscribe [::attachment language])]
    (atoms/download-action
     {:class :attachment-link
      :url (str "/api/licenses/attachments/" (:attachment-id attachment))
      :label (:attachment-filename attachment)})))

(defn- remove-attachment-action [language]
  (let [attachment @(rf/subscribe [::attachment language])]
    (atoms/delete-action
     {:label (text :t.form/attachment-remove)
      :on-click (fn [_]
                  (remove-attachment! {:attachment-id (:attachment-id attachment)
                                       :language language}))})))

(defn- license-attachment [language]
  (let [id (str "attachment-" (name language))
        attachment @(rf/subscribe [::attachment language])]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name language))]
     [:div.col-sm-11.d-flex.flex-wrap.align-items-center {:id id}
      (if (or (str/blank? (:attachment-filename attachment))
              (not= :success (:attachment-upload-status attachment)))
        [rems.attachment/upload-button (upload-attachment-action language)]
        [:div.d-flex.justify-content-start.gap-1
         [atoms/action-button (preview-attachment-action language)]
         [atoms/action-button (remove-attachment-action language)]])]]))

(defn- license-attachment-field []
  [:div.form-group.field
   [:label.administration-field-label
    (text :t.create-license/license-attachment)]
   [:div.mb-3
    [rems.attachment/allowed-extensions-info]]
   (into [:<>]
         (for [language @rems.config/languages]
           [license-attachment language]))])

(defn- save-license []
  (let [request (build-request @(rf/subscribe [::form])
                               @rems.config/languages)]
    (atoms/save-action {:id :save
                        :disabled (nil? request)
                        :on-click (when request
                                    #(rf/dispatch [::create-license request]))})))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/administration/licenses"
   (text :t.administration/cancel)])

(defn create-license-page []
  (let [current-license-type (:licensetype @(rf/subscribe [::form]))]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/create-license)]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-license"
       :title (text :t.administration/create-license)
       :always [:div.fields
                [license-organization-field]
                [license-type-radio-group]
                [license-title-field]
                (condp = current-license-type
                  license-type-link [license-link-field]
                  license-type-text [license-text-field]
                  license-type-attachment [license-attachment-field]
                  nil)
                [:div.col.commands
                 [cancel-button]
                 [perform-action-button (save-license)]]]}]]))

