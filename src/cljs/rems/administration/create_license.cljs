(ns rems.administration.create-license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field localized-textarea-autosize organization-field radio-button-group]]
            [rems.atoms :as atoms :refer [failure-symbol file-download document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.attachment-util :as attachment-util]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [format-file-size navigate! post! trim-when-string]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::form)}))

(rf/reg-sub ::form (fn [db _] (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

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

(defn- valid-localization? [data]
  (and (not (str/blank? (:title data)))
       (not (str/blank? (:textcontent data)))))

(defn- valid-request? [request languages]
  (and (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:licensetype request)))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

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

(defn- save-attachment [language form-data]
  (post! "/api/licenses/add_attachment"
         {:body form-data
          :handler (fn [response]
                     (rf/dispatch [::attachment-saved language (:id response)]))
          :error-handler (flash-message/default-error-handler :top "Save attachment")}))

(rf/reg-event-db
 ::attachment-saved
 (fn [db [_ language attachment-id]]
   (assoc-in db [::form :localizations language :attachment-id] attachment-id)))

(defn- remove-attachment [attachment-id]
  (post! "/api/licenses/remove_attachment"
         {:url-params {:attachment-id attachment-id}
          :body {}
          :error-handler (flash-message/default-error-handler :top "Remove attachment")}))

(rf/reg-event-fx
 ::save-attachment
 (fn [_ [_ language file]]
   (save-attachment language file)
   {}))

(rf/reg-event-db
 ::remove-attachment
 (fn [db [_ language attachment-id]]
   (when attachment-id
     (remove-attachment attachment-id))
   (assoc-in db [::form :localizations language :attachment-id] nil)))


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

(defn- set-attachment-event [language]
  (fn [event]
    (let [filecontent (aget (.. event -target -files) 0)
          form-data (doto (js/FormData.)
                      (.append "file" filecontent))]
      (rf/dispatch [::set-form-field [:localizations language :attachment-filename] (.-name filecontent)])
      (rf/dispatch [::save-attachment language form-data]))))

(defn- remove-attachment-event [language attachment-id]
  (fn [_]
    (rf/dispatch [::set-form-field [:localizations language :attachment-filename] nil])
    (rf/dispatch [::remove-attachment language attachment-id])))

(defn- license-attachment-field []
  (into [:div.form-group.field
         [:label.administration-field-label
          (text :t.create-license/license-attachment)]
         (let [allowed-extensions (->> attachment-util/allowed-extensions-string
                                       (text-format :t.form/upload-extensions))
               config @(rf/subscribe [:rems.config/config])]
           [:div.mb-3
            [:p allowed-extensions]
            [:p (->> (format-file-size (:attachment-max-size config))
                     (text-format :t.form/attachment-max-size))]])]
        (for [language @(rf/subscribe [:languages])
              :let [form @(rf/subscribe [::form])
                    filename (get-in form [:localizations language :attachment-filename])
                    attachment-id (get-in form [:localizations language :attachment-id])
                    id (str "attachment-" (name language))
                    input-id (str "upload-license-button-" (name language))
                    upload-status (get-in form [:localizations language :attachment-upload-status])]]
          [:div.form-group.row
           [:label.col-sm-1.col-form-label {:for id}
            (str/upper-case (name language))]
           [:div.col-sm-11.d-flex.flex-wrap.align-items-center {:id id}
            (if (empty? filename)
              [:div.upload-file.mr-2
               [:input {:style {:display "none"}
                        :type "file"
                        :id input-id
                        :accept attachment-util/allowed-extensions-string
                        :on-change (set-attachment-event language)}]
               [:button.btn.btn-secondary {:type :button
                                           :on-click #(.click (.getElementById js/document input-id))}
                (text :t.form/upload)]]
              [:div.d-flex.justify-content-start
               [:a.attachment-link.btn.btn-secondary.mr-2
                {:href (str "/api/licenses/attachments/" attachment-id)
                 :target :_blank}
                filename " " [file-download]]
               [:button.btn.btn-secondary.mr-2 {:type :button
                                                :on-click (remove-attachment-event language attachment-id)}
                (text :t.form/attachment-remove)]])
            [:span.ml-2
             (case upload-status
               :pending [spinner/small]
               :success nil ; the new attachment row appearing is confirmation enough
               :error [failure-symbol]
               nil)]]])))

(defn- save-license-button [on-click]
  (let [form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])
        request (build-request form languages)]
    [:button#save.btn.btn-primary
     {:type :button
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (on-click request))
      :disabled (nil? request)}
     (text :t.administration/save)]))

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
                 [save-license-button #(rf/dispatch [::create-license %])]]]}]]))

