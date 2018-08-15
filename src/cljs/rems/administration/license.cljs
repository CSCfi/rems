(ns rems.administration.license
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(def link-licensetype "link")
(def text-licensetype "text")

(defn parse-textcontent [form]
  (condp = (:licensetype form)
    link-licensetype (:link form)
    text-licensetype (:text form)
    nil))

(defn- create-license [form]
  (put! "/api/licenses/create" {:params {:title (:title form)
                                         :licensetype (:licensetype form)
                                         :textcontent (parse-textcontent form)}
                                :handler (fn [resp]
                                           (dispatch! "#/administration"))}))

(rf/reg-event-fx
  ::create-license
  (fn [db [_ form]]
    (create-license form)
    {}))

(rf/reg-event-db
  ::reset-create-license
  (fn [db _]
    (dissoc db ::form)))

(rf/reg-sub
  ::form
  (fn [db _]
    (::form db)))

(rf/reg-event-db
  ::set-form-field
  (fn [db [_ keys value]]
    (assoc-in db (concat [::form] keys) value)))


;;;; UI ;;;;

(defn- save-license-button []
  (let [form @(rf/subscribe [::form])]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-license form])
      :disabled (not (and (not (str/blank? (:title form)))
                          (not (str/blank? (:licensetype form)))
                          (not (str/blank? (parse-textcontent form)))))}
     (text :t.create-resource/save)]))                      ; TODO: rename translation key

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.create-catalogue-item/cancel)])                 ; TODO: rename translation key

(defn create-license-page []
  (let [form (rf/subscribe [::form])]
    (fn []
      ; TODO: translations
      [collapsible/component
       {:id "create-license"
        :title (text :t.navigation/create-license)
        :always [:div
                 [:div.form-group.field
                  [:label {:for "default-title"} "Title"]
                  [:input.form-control {:type "text"
                                        :id "default-title"
                                        :name "default-title"
                                        :value (:title @form)
                                        :on-change #(rf/dispatch [::set-form-field [:title] (.. % -target -value)])}]]
                 [:div.form-group.field
                  (let [value link-licensetype]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "link-licensetype-field"
                                               :name "licensetype-field"
                                               :value value
                                               :checked (= value (:licensetype @form))
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-form-field [:licensetype] value]))}]
                     [:label.form-check-label {:for "link-licensetype-field"} "External link"]])
                  (let [value text-licensetype]
                    [:div.form-check.form-check-inline
                     [:input.form-check-input {:type "radio"
                                               :id "text-licensetype-field"
                                               :name "licensetype-field"
                                               :value value
                                               :checked (= value (:licensetype @form))
                                               :on-change #(when (.. % -target -checked)
                                                             (rf/dispatch [::set-form-field [:licensetype] value]))}]
                     [:label.form-check-label {:for "text-licensetype-field"} "Inline text"]])]
                 (when (= link-licensetype (:licensetype @form))
                   [:div.form-group.field
                    [:label {:for "link-field"} "Link to the license"]
                    [:input.form-control {:type "text"
                                          :id "link-field"
                                          :name "link-field"
                                          :placeholder "https://example.com/license"
                                          :value (:link @form)
                                          :on-change #(rf/dispatch [::set-form-field [:link] (.. % -target -value)])}]])
                 (when (= text-licensetype (:licensetype @form))
                   [:div.form-group.field
                    [:label {:for "text-field"} "License text"]
                    [:textarea.form-control {:type "text"
                                             :id "text-field"
                                             :name "text-field"
                                             :value (:text @form)
                                             :on-change #(rf/dispatch [::set-form-field [:text] (.. % -target -value)])}]])
                 ; TODO: non-default languages
                 ; https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
                 [:div.col.commands
                  [cancel-button]
                  [save-license-button]]]}])))
