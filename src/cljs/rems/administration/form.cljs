(ns rems.administration.form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [re-frame.core :as rf]
            [rems.administration.components :refer [checkbox localized-text-field number-field radio-button-group text-field]]
            [rems.application :refer [enrich-user]]
            [rems.collapsible :as collapsible]
            [rems.text :refer [text text-format localize-item]]
            [rems.common-util :refer [vec-dissoc]]
            [rems.util :refer [dispatch! fetch post!]]))

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
   (assoc-in db [::form :items (count (:items (::form db)))] {})))

(rf/reg-event-db
 ::remove-form-item
 (fn [db [_ index]]
   (assoc-in db [::form :items] (vec-dissoc (:items (::form db)) index))))

(rf/reg-event-db
 ::move-form-item-up
 (fn [db [_ index]]
   (let [other (max 0 (dec index))]
     (-> db
         (assoc-in [::form :items index] (get-in db [::form :items other]))
         (assoc-in [::form :items other] (get-in db [::form :items index]))))))

(rf/reg-event-db
 ::move-form-item-down
 (fn [db [_ index]]
   (let [last-index (dec (count (get-in db [::form :items])))
         other (min last-index (inc index))]
     (-> db
         (assoc-in [::form :items index] (get-in db [::form :items other]))
         (assoc-in [::form :items other] (get-in db [::form :items index]))))))


;;;; form submit

(defn- supports-input-prompt? [item]
  (contains? #{"text" "texta" "description"} (:type item)))

(defn- supports-maxlength? [item]
  (contains? #{"text" "texta"} (:type item)))

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
       (if (= "option" (:type item))
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
         (when (= "option" (:type item))
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

(defn- form-item-title-field [item]
  [localized-text-field context {:keys [:items item :title]
                                 :label (text :t.create-form/item-title)}])

(defn- form-item-input-prompt-field [item]
  [localized-text-field context {:keys [:items item :input-prompt]
                                 :label (text :t.create-form/input-prompt)}])

(defn- form-item-maxlength-field [item]
  [number-field context {:keys [:items item :maxlength]
                       :label (text :t.create-form/maxlength)}])

(defn- form-item-type-radio-group [item]
  [radio-button-group context {:keys [:items item :type]
                               :orientation :vertical
                               :options [{:value "attachment", :label (text :t.create-form/type-attachment)}
                                         {:value "date", :label (text :t.create-form/type-date)}
                                         {:value "description", :label (text :t.create-form/type-description)}
                                         {:value "label", :label (text :t.create-form/type-label)}
                                         {:value "text", :label (text :t.create-form/type-text)}
                                         {:value "texta", :label (text :t.create-form/type-texta)}]}])

(defn- form-item-optional-checkbox [item]
  [checkbox context {:keys [:items item :optional]
                     :label (text :t.create-form/optional)}])

(defn- add-form-item-button []
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::add-form-item]))}
   (text :t.create-form/add-form-item)])

(defn- remove-form-item-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::remove-form-item index]))
    :aria-label (text :t.create-form/remove-form-item)
    :title (text :t.create-form/remove-form-item)}
   [:i.icon-link.fas.fa-times
    {:aria-hidden true}]])

(defn- move-form-item-up-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::move-form-item-up index]))
    :aria-label (text :t.create-form/move-form-item-up)
    :title (text :t.create-form/move-form-item-up)}
   [:i.icon-link.fas.fa-chevron-up
    {:aria-hidden true}]])

(defn- move-form-item-down-button [index]
  [:a
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (rf/dispatch [::move-form-item-down index]))
    :aria-label (text :t.create-form/move-form-item-down)
    :title (text :t.create-form/move-form-item-down)}
   [:i.icon-link.fas.fa-chevron-down
    {:aria-hidden true}]])

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

               (doall (for [item (range (count (:items form)))]
                        [:div.form-item
                         {:key item}
                         [:div.form-item-controls
                          [move-form-item-up-button item]
                          [move-form-item-down-button item]
                          [remove-form-item-button item]]

                         [form-item-title-field item]
                         [form-item-optional-checkbox item]
                         [form-item-type-radio-group item]
                         (when (supports-input-prompt? (get-in form [:items item]))
                           [form-item-input-prompt-field item])
                         (when (supports-maxlength? (get-in form [:items item]))
                           [form-item-maxlength-field item])]))

               [:div.form-item.new-form-item
                [add-form-item-button]]

               [:div.col.commands
                [cancel-button]
                [save-form-button]]]}]))
