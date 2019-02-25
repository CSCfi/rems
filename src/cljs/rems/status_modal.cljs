(ns rems.status-modal
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.guide-functions]
            [rems.modal :as modal]
            [rems.spinner :as spinner]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- status-widget [status error]
  [:div {:class (when (= :failed status) "alert alert-danger")}
   (condp contains? status
     #{nil} ""
     #{:pending} [spinner/big]
     #{:saved :success} [:div [:i {:class ["fa fa-check-circle text-success"]}] (text :t.form/success)]
     #{:failed} [:div [:i {:class "fa fa-times-circle text-danger"}]
                 (str (text :t.form/failed)
                      (when (:key error)
                        (str ": " (text (:key error))))
                      (when-let [text (:status-text error)]
                        (str ": " text))
                      (when-let [text (:status error)]
                        (str " (" text ")")))])])

(defn status-modal
  "Modal component showing the status of an action.

  `:status` - Show spinner while `:pending`. Either `:saved` or `:failed`
            status is shown with the internal status-widget that
            may show the `:error` contents.
  `:description` - the title of the modal.
  `:content` - additional content to show after the status widget.
  `:error` - error that may contain :key, :status and :status-text
           like translated errors or http errors
  `:on-close` - callback is called when the modal wants to close itself"
  [{:keys [description status error content on-close on-close-afer-error on-close-after-success]}]
  (cond
    (#{:saved :success} status)
    [modal/notification {:title description
                         :content [:div [status-widget status error] content]
                         :on-close on-close-after-success
                         :shade? true}]

    (= :failed status)
    [modal/notification {:title description
                         :content [:div [status-widget status error] content]
                         :on-close on-close-afer-error
                         :shade? true}]

    :default
    [modal/notification {:title description
                        :content [:div [status-widget status error] content]
                        :on-close on-close
                        :shade? true}]))

(defn example-wrapper [{:keys [opened-state component]}]
  (let [state (r/atom nil)
        on-close #(reset! state nil)]
    (fn [{:keys [opened-state componenent]}]
      [:div
       (when @state [component @state])
       [:button.btn.btn-secondary {:on-click #(reset! state (assoc opened-state :on-close on-close))} "Open modal"]])))

(defn status-modal-state-handling
  "Returns a map of modal options, that can be used by status-modal component

   The returned value is a map containing various event handlers, and most importantly,
   a state atom under the key :state-atom."
  [{:keys [on-success on-pending on-error on-close-after-success on-close-after-error] :as modal-options}]
  (let [state (r/atom nil)]
    (merge
     modal-options
     {:state-atom state
      :on-pending #(do
                     (swap! state assoc :status :pending)
                     (when on-pending (on-pending)))
      :on-success #(do
                     (swap! state assoc :status :success)
                     (when on-success (on-success)))
      :on-error #(do
                   (swap! state assoc :status :failed :error %)
                   (when on-error (on-error)))
      :on-close-after-success #(do
                                 (reset! state nil)
                                 (when on-close-after-success (on-close-after-success)))
      :on-close-after-error #(do
                               (reset! state nil)
                               (when on-close-after-error (on-close-after-error)))})))

(defn guide
  []
  [:div
   (component-info status-modal)
   (example "status-modal that opens as pending"
            [example-wrapper {:opened-state {:status :pending
                                             :description "Pending modal"}
                              :component status-modal}])
   (example "status-modal that opens as saved"
            [example-wrapper {:opened-state {:status :saved
                                             :description "Saved modal"}
                              :component status-modal}])
   (example "status-modal that opens as failed"
            [example-wrapper {:opened-state {:status :failed
                                             :error {:status 404
                                                     :status-text "Not found"}
                                             :description "Failed modal"}
                              :component status-modal}])])
