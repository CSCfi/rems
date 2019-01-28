(ns rems.administration.create-form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [re-frame.core :as rf]
            [rems.administration.components :refer [checkbox localized-text-field number-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.application :refer [enrich-user normalize-option-key]]
            [rems.collapsible :as collapsible]
            [rems.config :refer [dev-environment?]]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! post!]]))

(defn- reset-form [db]
  (assoc db ::form {:items []}))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (reset-form db)}))


;;;; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db
 ::add-form-item
 (fn [db [_]]
   (update-in db [::form :items] items/add {})))

(rf/reg-event-db
 ::remove-form-item
 (fn [db [_ item-index]]
   (update-in db [::form :items] items/remove item-index)))

(rf/reg-event-db
 ::move-form-item-up
 (fn [db [_ item-index]]
   (update-in db [::form :items] items/move-up item-index)))

(rf/reg-event-db
 ::move-form-item-down
 (fn [db [_ item-index]]
   (update-in db [::form :items] items/move-down item-index)))

(rf/reg-event-db
 ::add-form-item-option
 (fn [db [_ item-index]]
   (update-in db [::form :items item-index :options] items/add {})))

(rf/reg-event-db
 ::remove-form-item-option
 (fn [db [_ item-index option-index]]
   (update-in db [::form :items item-index :options] items/remove option-index)))

(rf/reg-event-db
 ::move-form-item-option-up
 (fn [db [_ item-index option-index]]
   (update-in db [::form :items item-index :options] items/move-up option-index)))

(rf/reg-event-db
 ::move-form-item-option-down
 (fn [db [_ item-index option-index]]
   (update-in db [::form :items item-index :options] items/move-down option-index)))


;;;; form submit

(defn- supports-input-prompt? [item]
  (contains? #{"text" "texta" "description"} (:type item)))

(defn- supports-maxlength? [item]
  (contains? #{"text" "texta"} (:type item)))

(defn- supports-options? [item]
  (contains? #{"option" "multiselect"} (:type item)))

(defn- localized-string? [lstr languages]
  (and (= (set (keys lstr))
          (set languages))
       (every? string? (vals lstr))))

(defn- valid-required-localized-string? [lstr languages]
  (and (localized-string? lstr languages)
       (every? #(not (str/blank? %))
               (vals lstr))))

(defn- valid-optional-localized-string? [lstr languages]
  (and (localized-string? lstr languages)
       ;; partial translations are not allowed
       (or (every? #(not (str/blank? %))
                   (vals lstr))
           (every? str/blank?
                   (vals lstr)))))

(defn- valid-option? [option languages]
  (and (not (str/blank? (:key option)))
       (valid-required-localized-string? (:label option) languages)))

(defn- valid-request-item? [item languages]
  (and (valid-required-localized-string? (:title item) languages)
       (boolean? (:optional item))
       (not (str/blank? (:type item)))
       (if (supports-input-prompt? item)
         (valid-optional-localized-string? (:input-prompt item) languages)
         (nil? (:input-prompt item)))
       (if (supports-options? item)
         (every? #(valid-option? % languages) (:options item))
         (nil? (:options item)))))

(defn- valid-request? [request languages]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:title request)))
       (every? #(valid-request-item? % languages) (:items request))))

(defn build-localized-string [lstr languages]
  (into {} (for [language languages]
             [language (get lstr language "")])))

(defn- build-request-item [item languages]
  (merge {:title (build-localized-string (:title item) languages)
          :optional (boolean (:optional item))
          :type (:type item)}
         (when (supports-input-prompt? item)
           {:input-prompt (build-localized-string (:input-prompt item) languages)})
         (when (supports-maxlength? item)
           {:maxlength (when-not (str/blank? (:maxlength item))
                         (parseInt (:maxlength item)))})
         (when (supports-options? item)
           {:options (for [{:keys [key label]} (:options item)]
                       {:key key
                        :label (build-localized-string label languages)})})))

(defn build-request [form languages]
  (let [request {:organization (:organization form)
                 :title (:title form)
                 :items (mapv #(build-request-item % languages) (:items form))}]
    (when (valid-request? request languages)
      request)))

(defn- create-form [request]
  (post! "/api/forms/create" {:params request
                              ; TODO: error handling
                              :handler (fn [resp] (dispatch! "#/administration"))}))

(rf/reg-event-fx
 ::create-form
 (fn [_ [_ request]]
   (create-form request)
   {}))


;;;; UI

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- form-organization-field []
  [text-field context {:keys [:organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- form-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-form/title)}])

(defn- form-item-title-field [item-index]
  [localized-text-field context {:keys [:items item-index :title]
                                 :label (text :t.create-form/item-title)}])

(defn- form-item-input-prompt-field [item-index]
  [localized-text-field context {:keys [:items item-index :input-prompt]
                                 :label (text :t.create-form/input-prompt)}])

(defn- form-item-maxlength-field [item-index]
  [number-field context {:keys [:items item-index :maxlength]
                         :label (text :t.create-form/maxlength)}])

(defn- add-form-item-option-button [item-index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::add-form-item-option item-index]))}
   (text :t.create-form/add-option)])

(defn- remove-form-item-option-button [item-index option-index]
  [items/remove-button #(rf/dispatch [::remove-form-item-option item-index option-index])])

(defn- move-form-item-option-up-button [item-index option-index]
  [items/move-up-button #(rf/dispatch [::move-form-item-option-up item-index option-index])])

(defn- move-form-item-option-down-button [item-index option-index]
  [items/move-down-button #(rf/dispatch [::move-form-item-option-down item-index option-index])])

(defn- form-item-option-field [item-index option-index]
  [:div.form-item-option
   [:div.form-item-header
    [:h4 (text-format :t.create-form/option-n (inc option-index))]
    [:div.form-item-controls
     [move-form-item-option-up-button item-index option-index]
     [move-form-item-option-down-button item-index option-index]
     [remove-form-item-option-button item-index option-index]]]
   [text-field context {:keys [:items item-index :options option-index :key]
                        :label (text :t.create-form/option-key)
                        :normalizer normalize-option-key}]
   [localized-text-field context {:keys [:items item-index :options option-index :label]
                                  :label (text :t.create-form/option-label)}]])

(defn- form-item-option-fields [item-index]
  (let [form @(rf/subscribe [::form])]
    (into (into [:div]
                (for [option-index (range (count (get-in form [:items item-index :options])))]
                  [form-item-option-field item-index option-index]))
          [[:div.form-item-option.new-form-item-option
            [add-form-item-option-button item-index]]])))

(defn- form-item-type-radio-group [item-index]
  [radio-button-group context {:keys [:items item-index :type]
                               :orientation :vertical
                               :options [{:value "text", :label (text :t.create-form/type-text)}
                                         {:value "texta", :label (text :t.create-form/type-texta)}
                                         {:value "description", :label (text :t.create-form/type-description)}
                                         {:value "option", :label (text :t.create-form/type-option)}
                                         {:value "multiselect", :label (text :t.create-form/type-multiselect)}
                                         {:value "date", :label (text :t.create-form/type-date)}
                                         {:value "attachment", :label (text :t.create-form/type-attachment)}
                                         {:value "label", :label (text :t.create-form/type-label)}]}])

(defn- form-item-optional-checkbox [item-index]
  [checkbox context {:keys [:items item-index :optional]
                     :label (text :t.create-form/optional)}])

(defn- add-form-item-button []
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::add-form-item]))}
   (text :t.create-form/add-form-item)])

(defn- remove-form-item-button [item-index]
  [items/remove-button #(rf/dispatch [::remove-form-item item-index])])

(defn- move-form-item-up-button [item-index]
  [items/move-up-button #(rf/dispatch [::move-form-item-up item-index])])

(defn- move-form-item-down-button [item-index]
  [items/move-down-button #(rf/dispatch [::move-form-item-down item-index])])

(defn- save-form-button []
  (let [form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])
        request (build-request form languages)]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-form request])
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn create-form-page []
  (let [form @(rf/subscribe [::form])]
    [collapsible/component
     {:id "create-form"
      :title (text :t.administration/create-form)
      :always [:div
               [form-organization-field]
               [form-title-field]

               (doall (for [item-index (range (count (:items form)))]
                        [:div.form-item
                         {:key item-index}
                         [:div.form-item-header
                          [:h4 (text-format :t.create-form/item-n (inc item-index))]
                          [:div.form-item-controls
                           [move-form-item-up-button item-index]
                           [move-form-item-down-button item-index]
                           [remove-form-item-button item-index]]]

                         [form-item-title-field item-index]
                         [form-item-optional-checkbox item-index]
                         [form-item-type-radio-group item-index]
                         (when (supports-input-prompt? (get-in form [:items item-index]))
                           [form-item-input-prompt-field item-index])
                         (when (supports-maxlength? (get-in form [:items item-index]))
                           [form-item-maxlength-field item-index])
                         (when (supports-options? (get-in form [:items item-index]))
                           [form-item-option-fields item-index])]))

               [:div.form-item.new-form-item
                [add-form-item-button]]

               [:div.col.commands
                [cancel-button]
                [save-form-button]]]}]))
