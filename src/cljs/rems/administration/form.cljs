(ns rems.administration.form
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [info-field readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id]]
   {:db (assoc db ::loading? true)
    ::fetch-form [form-id]}))

(defn- fetch-form [form-id]
  (fetch (str "/api/forms/" form-id)
         {:handler #(rf/dispatch [::fetch-form-result %])}))

(rf/reg-fx ::fetch-form (fn [[form-id]] (fetch-form form-id)))

(rf/reg-event-db
 ::fetch-form-result
 (fn [db [_ form]]
   (-> db
       (assoc ::form form)
       (dissoc ::loading?))))

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/back)])

(defn- to-create-form []
  [:a.btn.btn-primary
   {:href "/#/administration/create-form"}
   (text :t.administration/create-form)])

(defn inline-info-field [text value]
  [info-field text value {:inline? true}])

(defn get-localized-value [field key language]
  (key (first (filter (comp #{(name language)} :langcode)
                      (:localizations field)))))

(defn form-field [field language]
  (into [:div.form-item
         [:h4 (text-format :t.administration/field (get-localized-value field :title language))]]
        (concat
         (for [localization (:localizations field)]
           [inline-info-field (str (text :t.administration/title)
                                   " "
                                   (str/upper-case (name (:langcode localization)))) (:title localization)])
         [[inline-info-field (text :t.create-form/type) (text (keyword (str "t.create-form/type-" (:type field))))]
          [inline-info-field (text :t.create-form/input-prompt) (get-localized-value field :inputprompt language)]
          [inline-info-field (text :t.create-form/optional) [readonly-checkbox (:formitemoptional field)]]
          [inline-info-field (text :t.create-form/maxlength) (:maxlength field)]])))

(defn form-fields [fields language]
  (into [:div]
        (for [field (sort-by :itemorder fields)]
          [form-field field language])))

(defn form-view [form language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "form"
     :title [:span (:organization form) "/" (:title form)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization form)]
              [inline-info-field (text :t.administration/title) (:title form)]
              [inline-info-field (text :t.administration/start) (localize-time (:start form))]
              [inline-info-field (text :t.administration/end) (localize-time (:end form))]
              [inline-info-field (text :t.administration/active) (str (:active form))]
              [:div.col.commands
               [back-button]]]}]
   [collapsible/component
    {:id "fields"
     :title [:span (text :t.administration/fields)]
     :collapse [form-fields (:fields form) language]}]
   ;; TODO Do we support form licenses?
   ])

(defn form-page []
  (let [form (rf/subscribe [::form])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [:h2 (text :t.administration/form)]
       (if @loading?
         [spinner/big]
         [form-view @form @language])])))
