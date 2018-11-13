(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.components :refer [radio-button-group text-field]]
            [rems.application :refer [enrich-user]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [vec-dissoc]]
            [rems.config :refer [dev-environment?]]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format localize-item]]
            [rems.util :refer [dispatch! fetch post!]]))

(defn- reset-form [db]
  (assoc db
         ::form {:type :auto-approve
                 :rounds []}
         ::loading? true))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (reset-form db)
    ::fetch-actors nil}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

;;; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

;;; form submit

(defn- valid-round? [round]
  (and (not (nil? (:type round)))
       (not (empty? (:actors round)))))

(defn- valid-request? [request]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:title request)))
       (case (:type request)
         :auto-approve true
         :dynamic (not (empty? (:handlers request)))
         :rounds (every? valid-round? (:rounds request))
         nil false)))

(defn- build-request-user [actor]
  {:userid (:userid actor)})

(defn- build-request-round [round]
  {:type (:type round)
   :actors (map build-request-user (:actors round))})

(defn build-request [form]
  (let [request {:organization (:organization form)
                 :title (:title form)
                 :type (:type form)}
        request (case (:type form)
                  :auto-approve request
                  :dynamic (assoc request :handlers (map build-request-user (:handlers form)))
                  :rounds (assoc request :rounds (map build-request-round (:rounds form)))
                  nil nil)]
    (when (valid-request? request)
      request)))

(defn- create-workflow [request]
  (post! "/api/workflows/create" {:params request
                                  ; TODO: error handling
                                  :handler (fn [resp] (dispatch! "#/administration"))}))

(rf/reg-event-fx
 ::create-workflow
 (fn [_ [_ request]]
   (create-workflow request)
   {}))


;;; selected actors

(defn- remove-actor [actors actor]
  (filter #(not= (:userid %)
                 (:userid actor))
          actors))

(rf/reg-event-db
 ::remove-actor
 (fn [db [_ round actor]]
   (update-in db [::form :rounds round :actors] remove-actor actor)))

(defn- add-actor [actors actor]
  (-> actors
      (remove-actor actor) ; avoid duplicates
      (conj actor)))

(rf/reg-event-db
 ::add-actor
 (fn [db [_ round actor]]
   (update-in db [::form :rounds round :actors] add-actor actor)))


;;; selected handlers

(rf/reg-event-db
 ::remove-handler
 (fn [db [_ handler]]
   (update-in db [::form :handlers] remove-actor handler)))

(rf/reg-event-db
 ::add-handler
 (fn [db [_ handler]]
   (update-in db [::form :handlers] add-actor handler)))


;;; available actors

(defn- fetch-actors []
  (fetch "/api/workflows/actors" {:handler #(rf/dispatch [::fetch-actors-result %])}))

(rf/reg-fx
 ::fetch-actors
 (fn [_]
   (fetch-actors)))

(rf/reg-event-db
 ::fetch-actors-result
 (fn [db [_ actors]]
   (-> db
       (assoc ::actors (map enrich-user actors))
       (dissoc ::loading?))))

(rf/reg-sub
 ::actors
 (fn [db _]
   (::actors db)))


;;;; UI ;;;;

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- workflow-organization-field []
  [text-field context {:keys [:organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- workflow-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(defn- workflow-type-field []
  [radio-button-group context {:keys [:type]
                               :orientation :horizontal
                               :options (remove #(and (not (dev-environment?)) ;; TODO: remove this feature toggle after dynamic workflow is implemented
                                                      (= :dynamic (:value %)))
                                                [{:value :auto-approve
                                                  :label (text :t.create-workflow/auto-approve-workflow)}
                                                 {:value :dynamic
                                                  :label (text :t.create-workflow/dynamic-workflow)}
                                                 {:value :rounds
                                                  :label (text :t.create-workflow/rounds-workflow)}])}])

(defn- round-type-radio-group [round]
  [radio-button-group context {:keys [:rounds round :type]
                               :orientation :horizontal
                               :options [{:value :approval
                                          :label (text :t.create-workflow/approval-round)}
                                         {:value :review
                                          :label (text :t.create-workflow/review-round)}]}])

(defn- workflow-actors-field [round]
  (let [form @(rf/subscribe [::form])
        round-type (get-in form [:rounds round :type])
        all-actors @(rf/subscribe [::actors])
        selected-actors (get-in form [:rounds round :actors])]
    (when round-type
      [:div.form-group
       [:label (case round-type
                 :approval (text :t.create-workflow/approvers)
                 :review (text :t.create-workflow/reviewers))]
       [autocomplete/component
        {:value (sort-by :userid selected-actors)
         :items all-actors
         :value->text #(:display %2)
         :item->key :userid
         :item->text :display
         :item->value identity
         :search-fields [:display :userid]
         :add-fn #(rf/dispatch [::add-actor round %])
         :remove-fn #(rf/dispatch [::remove-actor round %])}]])))

(defn- next-workflow-arrow []
  [:i.next-workflow-arrow.fa.fa-long-arrow-alt-down
   {:aria-hidden true}])

(defn- add-round-button []
  (let [form @(rf/subscribe [::form])]
    [:a
     {:href "#"
      :on-click (fn [event]
                  (.preventDefault event)
                  ; TODO: refactor to re-frame events
                  (rf/dispatch [::set-form-field [:rounds (count (:rounds form))] {}]))}
     (text :t.create-workflow/add-round)]))

(defn- remove-round-button [round]
  (let [form @(rf/subscribe [::form])]
    [:a.remove-workflow-round
     {:href "#"
      :on-click (fn [event]
                  (.preventDefault event)
                  ; TODO: refactor to re-frame events
                  (rf/dispatch [::set-form-field [:rounds] (vec-dissoc (:rounds form) round)]))
      :aria-label (text :t.create-workflow/remove-round)
      :title (text :t.create-workflow/remove-round)}
     [:i.icon-link.fas.fa-times
      {:aria-hidden true}]]))

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])
        request (build-request form)]
    [:button.btn.btn-primary
     {:on-click #(rf/dispatch [::create-workflow request])
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration")}
   (text :t.administration/cancel)])

(defn workflow-type-description [description]
  [:div.alert.alert-info description])

(defn round-workflow-form []
  (let [form @(rf/subscribe [::form])
        num-rounds (count (:rounds form))
        last-round (dec num-rounds)]
    [:div
     [workflow-type-description (text :t.create-workflow/rounds-workflow-description)]
     (doall (for [round (range num-rounds)]
              [:div.workflow-round
               {:key round}
               [remove-round-button round]

               [:h2 (text-format :t.create-workflow/round-n (inc round))]
               [round-type-radio-group round]
               [workflow-actors-field round]
               (when (not= round last-round)
                 [next-workflow-arrow])]))

     [:div.workflow-round.new-workflow-round
      [add-round-button]]]))

(defn- workflow-handlers-field []
  (let [form @(rf/subscribe [::form])
        all-handlers @(rf/subscribe [::actors])
        selected-handlers (get-in form [:handlers])]
    [:div.form-group
     [:label (text :t.create-workflow/handlers)]
     [autocomplete/component
      {:value (sort-by :userid selected-handlers)
       :items all-handlers
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:display :userid]
       :add-fn #(rf/dispatch [::add-handler %])
       :remove-fn #(rf/dispatch [::remove-handler %])}]]))

(defn dynamic-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/dynamic-workflow-description)]
   [workflow-handlers-field]])

(defn auto-approve-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/auto-approve-workflow-description)]])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])
        workflow-type (:type form)
        loading? (rf/subscribe [::loading?])]
    [collapsible/component
     {:id "create-workflow"
      :title (text :t.administration/create-workflow)
      :always [:div
               (if @loading?
                 [:div#workflow-loader [spinner/big]]
                 [:div#workflow-editor
                  [workflow-organization-field]
                  [workflow-title-field]
                  [workflow-type-field]

                  (case workflow-type
                    :auto-approve [auto-approve-workflow-form]
                    :dynamic [dynamic-workflow-form]
                    :rounds [round-workflow-form])

                  [:div.col.commands
                   [cancel-button]
                   [save-workflow-button]]])]}]))
