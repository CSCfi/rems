(ns rems.administration.create-license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [radio-button-group text-field textarea-autosize]]
            [rems.atoms :as atoms :refer [file-download document-title]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [navigate! post! trim-when-string]]))

(rf/reg-event-db
 ::enter-page
 (fn [db _]
   (dissoc db ::form)))


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
  (and (not (str/blank? (:licensetype request)))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

(defn build-request [form languages]
  (let [license-type (:licensetype form)
        request {:licensetype license-type
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

(defn- language-heading [language]
  [:h3 (str/upper-case (name language))])

(defn- license-title-field [language]
  [text-field context {:keys [:localizations language :title]
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

(defn- current-license-type []
  (let [form @(rf/subscribe [::form])]
    (:licensetype form)))

(defn- license-link-field [language]
  (when (= license-type-link (current-license-type))
    [text-field context {:keys [:localizations language :link]
                         :label (text :t.create-license/link-to-license)
                         :placeholder "https://example.com/license"}]))

(defn- license-text-field [language]
  (when (= license-type-text (current-license-type))
    [textarea-autosize context {:keys [:localizations language :text]
                                :label (text :t.create-license/license-text)}]))

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

(defn- license-attachment-field [language]
  (when (= license-type-attachment (current-license-type))
    (let [form @(rf/subscribe [::form])
          filename (get-in form [:localizations language :attachment-filename])
          attachment-id (get-in form [:localizations language :attachment-id])
          filename-field [:a.btn.btn-secondary.mr-2
                          {:href (str "/api/licenses/attachments/" attachment-id)
                           :target :_blank}
                          filename " " [file-download]]
          upload-field [:div.upload-file.mr-2
                        [:input {:style {:display "none"}
                                 :type "file"
                                 :id "upload-license-button"
                                 :accept ".pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
                                 :on-change (set-attachment-event language)}]
                        [:button.btn.btn-secondary
                         {:type :button
                          :on-click #(.click (.getElementById js/document "upload-license-button"))}
                         (text :t.form/upload)]]
          remove-button [:button.btn.btn-secondary.mr-2
                         {:type :button
                          :on-click (remove-attachment-event language attachment-id)}
                         (text :t.form/attachment-remove)]]
      (if (empty? filename)
        upload-field
        [:div {:style {:display :flex :justify-content :flex-start}}
         filename-field
         remove-button]))))

(defn- save-license-button [on-click]
  (let [form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])
        request (build-request form languages)]
    [:button.btn.btn-primary
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
  (let [languages @(rf/subscribe [:languages])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/create-license)]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-license"
       :title (text :t.administration/create-license)
       :always [:div
                [license-type-radio-group]
                (for [language languages]
                  [:div {:key language}
                   [language-heading language]
                   [license-title-field language]
                   [license-link-field language]
                   [license-text-field language]
                   [license-attachment-field language]])
                [:div.col.commands
                 [cancel-button]
                 [save-license-button #(rf/dispatch [::create-license %])]]]}]]))
