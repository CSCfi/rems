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
   (case status
     nil ""
     :pending [spinner/big]
     :saved [:div [:i {:class ["fa fa-check-circle text-success"]}] (text :t.form/success)]
     :failed [:div [:i {:class "fa fa-times-circle text-danger"}]
              (str (text :t.form/failed)
                   (when (:key error)
                     (str ": " (text (:key error))))
                   (when-let [text (:status-text error)]
                     (str ": " text))
                   (when-let [text (:status error)]
                     (str " (" text ")")))])])

(defn status-modal
  "Modal component showing the status of an action.

  `:status` - Show spinner while `:pending`. Either `:saved` or `:failed` status is shown with the internal status-widget that may show the `:error` contents.
  `:description` - the title of the modal.
  `:content` - additional content to show after the status widget.
  `:on-close` - callback is called when the modal wants to close itself"
  [{:keys [description status error content on-close]}]
  [modal/notification {:title description
                       :content [:div [status-widget status error] content]
                       :on-close on-close
                       :shade? true}])

(defn example-wrapper [{:keys [opened-state component]}]
  (let [state (r/atom nil)
        on-close #(reset! state nil)]
    (fn [{:keys [opened-state componenent]}]
      [:div
       (when @state [component @state])
       [:button.btn.btn-secondary {:on-click #(reset! state (assoc opened-state :on-close on-close))} "Open modal"]])))

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
