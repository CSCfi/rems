(ns rems.application
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Events and actions ;;;;

(rf/reg-event-fx
 ::start-fetch-application
 (fn [coeff [_ id]]
   {::fetch-application [(get-in coeff [:db :user]) id]}))

(defn- fetch-application [user id]
  (GET (str "/api/application/" id) {:handler #(rf/dispatch [::fetch-application-result %])
                                     :response-format :json
                                     :headers {"x-rems-user-id" (:eppn user)}
                                     :keywords? true}))

(rf/reg-fx
 ::fetch-application
 (fn [[user id]]
   (fetch-application user id)))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db :application application)))

;;;; UI components ;;;;

;; Fields

(defn- id-to-name [id]
  (str "field" id))

(defn- text-field
  [{title :title
    id :id
    prompt :inputprompt
    value :value
    optional :optional
    readonly :readonly}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value value :readonly readonly}]])

(defn- texta-field
  [{title :title
    id :id
    prompt :inputprompt
    value :value
    optional :optional
    readonly :readonly}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt
                            :readonly readonly}
    value]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- license [id readonly approved content]
  [:div.row
   [:div.col-1
    [:input (merge {:type "checkbox" :name (str "license" id) :value "approved"
                    :disabled readonly}
                   (when approved {:checked ""}))]]
   [:div.col content]])

(defn- link-license
  [{title :title id :id textcontent :textcontent approved :approved readonly :readonly}]
  (license id readonly approved
           [:a {:href textcontent :target "_blank"}
            title " "]))

(defn- text-license
  [{title :title id :id textcontent :textcontent approved :approved readonly :readonly}]
  (license id readonly approved
           [:div.license-panel
            [:h6.license-title
             [:a.license-header.collapsed {:data-toggle "collapse"
                                           :href (str "#collapse" id)
                                           :aria-expanded "false"
                                           :aria-controls (str "collapse" id)}
              title " " [:i {:class "fa fa-ellipsis-h"}]]]
            [:div.collapse {:id (str "collapse" id) }
             [:div.license-block textcontent]]]))

(defn- unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(defn- field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    "label" (label f)
    "license" (case (:licensetype f)
                "link" (link-license f)
                "text" (text-license f)
                (unsupported-field f))
    (unsupported-field f)))

(defn- fields [form]
  (let [application (:application form)
        state (:state application)
        editable? (= "draft" state)
        readonly? (not editable?)]
    (collapsible/component
     {:id "form"
      :class "slow"
      :open? true
      :title (text :t.form/application)
      :collapse
      [:div
       (into [:div]
             (for [i (:items form)]
               (field (assoc i :readonly readonly?))))
       (when-let [licenses (not-empty (:licenses form))]
         [:div.form-group.field
          [:h4 (text :t.form/licenses)]
          (for [l licenses]
            (field (assoc l :readonly readonly?)))])]})))

;; Whole application

(defn- render-application [application]
  [:pre (with-out-str (cljs.pprint/pprint application))])

;;;; Entrypoint ;;;;

(defn- show-application []
  (if-let [application @(rf/subscribe [:application])]
    (render-application application)
    [:p "No application loaded"]))

(defn application-page []
  (show-application))

;;;; Guide ;;;;

(def ^:private lipsum
  (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
       "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim "
       "veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex "
       "ea commodo consequat. Duis aute irure dolor in reprehenderit in "
       "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur "
       "sint occaecat cupidatat non proident, sunt in culpa qui officia "
       "deserunt mollit anim id est laborum."))

(def ^:private +example+
  {:id 1,
 :catalogue-items
 [{:id 2,
   :title "ELFA Corpus, one approval",
   :wfid 2,
   :formid 1,
   :resid "http://urn.fi/urn:nbn:fi:lb-201403262",
   :state "enabled",
   :langcode "en",
   :localizations
   {:en {:id 2, :langcode "en", :title "ELFA Corpus, one approval"},
    :fi
    {:id 2, :langcode "fi", :title "ELFA-korpus, yksi hyväksyntä"}}}],
 :applicant-attributes
 {:eppn "developer", :mail "deve@lo.per", :commonName "developer"},
 :application
 {:applicantuserid "developer",
  :can-approve? false,
  :events [],
  :review-type nil,
  :start "2018-01-26T07:51:01.467Z",
  :state "draft",
  :wfid 2,
  :fnlround 0,
  :id 1,
  :can-close? true,
  :curround 0},
 :licenses
 [{:id 2,
   :type "license",
   :licensetype "link",
   :title "CC Attribution 4.0",
   :textcontent
   "https://creativecommons.org/licenses/by/4.0/legalcode",
   :approved true}
  {:id 3,
   :type "license",
   :licensetype "text",
   :title "General Terms of Use",
   :textcontent
   "License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. ",
   :approved true}],
 :title "Yksinkertainen lomake",
 :items
 [{:id 1,
   :title "Project name",
   :inputprompt "Project",
   :optional false,
   :type "text",
   :value "draft application"}
  {:id 3,
   :title "Duration of the project",
   :inputprompt "YYYY-YYYY",
   :optional true,
   :type "text",
   :value "draft application"}
  {:id 2,
   :title "Purpose of the project",
   :inputprompt "The purpose of the project is to ...",
   :optional false,
   :type "texta",
   :value "draft application"}]})

(defn guide []
  [:div
   (component-info field)
   (example "field of type \"text\""
            [:form
             (field {:type "text" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"texta\""
            [:form
             (field {:type "texta" :title "Title" :inputprompt "prompt"})])
   (example "optional field"
            [:form
             (field {:type "texta" :optional "true" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"label\""
            [:form
             (field {:type "label" :title "Lorem ipsum dolor sit amet"})])
   (example "link license"
            [:form
             (field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"})])
   (example "text license"
            [:form
             (field {:type "license" :id 1 :title "A Text License" :licensetype "text"
                     :textcontent lipsum})])

   (component-info fields)
   (example "fields, partially filled"
            (fields {:title "Form title"
                     :application {:id 17 :state "draft"
                                   :can-approve? false
                                   :can-close? false
                                   :review-type nil}
                     :catalogue-items [{:title "An applied item"}]
                     :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                             {:type "label" :title "Please input your wishes below."}
                             {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                             {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                     :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 2
                                 :textcontent lipsum}
                                {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                                 :approved true}]}))
   (example "fields, applied"
            (fields{:title "Form title"
                    :application {:id 17 :state "applied"
                                  :can-approve? false
                                  :can-close? true
                                  :review-type nil}
                    :catalogue-items [{:title "An applied item"}]
                    :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                            {:type "label" :title "Please input your wishes below."}
                            {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                            {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                    :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 3
                                :textcontent lipsum}
                               {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                                :approved true}]
                    :comments [{:comment "a comment"}]}))

   (component-info render-application)
   (example "whole application"
            (render-application +example+))])
