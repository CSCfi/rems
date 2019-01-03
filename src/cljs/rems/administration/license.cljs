(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.components :refer [radio-button-group text-field texta-field]]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text localize-item]]
            [rems.util :refer [dispatch! fetch post!]]))

(defn- reset-form [db]
  (dissoc db ::form))

(rf/reg-event-db
 ::enter-page
 (fn [db _]
   (reset-form db)))


; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))


; form submit

(def license-type-link "link")
(def license-type-text "text")

(defn parse-textcontent [form license-type]
  (case license-type
    "link" (:link form)
    "text" (:text form)
    nil))

(defn- build-localization [data license-type]
  {:title (:title data)
   :textcontent (parse-textcontent data license-type)})

(defn- valid-localization? [data]
  (and (not (str/blank? (:title data)))
       (not (str/blank? (:textcontent data)))))

(defn- valid-request? [request languages]
  (and (not (str/blank? (:licensetype request)))
       (= (set languages)
          (set (keys (:localizations request))))
       (every? valid-localization? (vals (:localizations request)))))

(defn build-request [form default-language languages]
  (let [license-type (:licensetype form)
        request {:licensetype license-type
                 :localizations (into {} (map (fn [[lang data]]
                                                [lang (build-localization data license-type)])
                                              (:localizations form)))}]
    (when (valid-request? request languages)
      (localize-item request default-language))))

(defn- create-license [request]
  (post! "/api/licenses/create" {:params request
                                 ; TODO: error handling
                                 :handler (fn [resp] (dispatch! "#/administration"))}))

(rf/reg-event-fx
 ::create-license
 (fn [_ [_ request]]
   (create-license request)
   {}))


;;;; UI

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- language-heading [language]
  [:h2 (str/upper-case (name language))])

(defn- license-title-field [language]
  [text-field context {:keys [:localizations language :title]
                       :label (text :t.create-license/title)}])

(defn- license-type-radio-group []
  [radio-button-group context {:keys [:licensetype]
                               :orientation :horizontal
                               :options [{:value license-type-link
                                          :label (text :t.create-license/external-link)}
                                         {:value license-type-text
                                          :label (text :t.create-license/inline-text)}]}])

(defn- current-licence-type []
  (let [form @(rf/subscribe [::form])]
    (:licensetype form)))

(defn- license-link-field [language]
  (when (= license-type-link (current-licence-type))
    [text-field context {:keys [:localizations language :link]
                         :label (text :t.create-license/link-to-license)
                         :placeholder "https://example.com/license"}]))

(defn- license-text-field [language]
  (when (= license-type-text (current-licence-type))
    [texta-field context {:keys [:localizations language :text]
                         :label (text :t.create-license/license-text)}]))

(defn- save-license-button []
  (let [form @(rf/subscribe [::form])
        default-language @(rf/subscribe [:default-language])
        languages @(rf/subscribe [:languages])
        request (build-request form default-language languages)]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-license request])
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-license-page []
  (let [default-language @(rf/subscribe [:default-language])
        languages @(rf/subscribe [:languages])]
    [collapsible/component
     {:id "create-license"
      :title (text :t.navigation/create-license)
      :always [:div
               [language-heading default-language]
               [license-title-field default-language]
               [license-type-radio-group]
               [license-link-field default-language]
               [license-text-field default-language]

               (doall (for [language (remove #(= % default-language) languages)]
                        [:div {:key language}
                         [language-heading language]
                         [license-title-field language]
                         [license-link-field language]
                         [license-text-field language]]))

               [:div.col.commands
                [cancel-button]
                [save-license-button]]]}]))
