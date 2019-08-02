(ns rems.status-modal
  "Component for showing a status modal dialog.

  There should only be one `status-modal` at the root of the component hierarchy.

  Use the functions `set-pending!`, `set-success!` and `set-error!` to control its state.
  See `rems.status-modal/status-modal` for values to use in the calls."
  (:require [re-frame.core :as rf]
            [rems.common-util :refer [deep-merge]]
            [rems.guide-functions]
            [rems.modal :as modal]
            [rems.spinner :as spinner]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- format-errors [errors]
  (into [:<>]
        (for [error errors]
          [:p
           (when (:key error)
             (text (:key error)))
           (when (:type error)
             (text (:type error)))
           (when-let [text (:status-text error)] text)
           (when-let [text (:status error)]
             (str " (" text ")"))])))

(defn- status-icon [success? error-content]
  (cond
    (and (not success?) (not error-content)) [spinner/small]
    success? [:span.fa-stack {:aria-label (text :t.form/success)}
              [:i {:class "fas fa-circle fa-stack-1x icon-stack-background"}]
              [:i {:class "fas fa-check-circle fa-stack-1x text-success"}]]
    error-content [:span.fa-stack {:aria-label (text :t.form/failed)}
                   [:i {:class "fas fa-circle fa-stack-1x icon-stack-background"}]
                   [:i {:class "fas fa-times-circle fa-stack-1x text-danger"}]]))

(defn- status-text [success? error-content]
  (cond
    (and (not success?) (not error-content)) [:p (text :t.form/please-wait)]
    success? [:p#status-success (text :t.form/success)]
    error-content [:<>
                   [:p#status-failed (text :t.form/failed)]
                   error-content]))

(rf/reg-event-db ::set-state (fn [db [_ state]] (assoc db ::state state)))
(rf/reg-event-db ::merge-state (fn [db [_ state]] (update db ::state deep-merge state)))
(rf/reg-sub ::state (fn [db _] (::state db)))

(defn close []
  (rf/dispatch [::set-state nil]))

(defn status-modal
  "Modal component showing the status of an action.

   `initial-state` can contain:

  `:open?`         - A boolean indicating if the modal is shown or not.
  `:result`        - Either {:success? true} one of {:error ...} or {:errors ...}
                     Shows the status based on the values or a spinner while neither.
    `:error`       - error that may contain `:key`, `:type`, `:status` and `:status-text`
                     like translated errors or http errors
    `:errors`      - seq like `:error`

  `:title`         - title of the modal, i.e. name of the operation
  `:content`       - additional content to show after the status widget
  `:error-content` - content to show instead of generated content from result errors
  `:on-close`      - callback is called when the modal wants to close itself

  Also when setting global state with `set-pending!`, `set-success!` or `set-error!`
  the same structure applies."
  [& [initial-state]]
  (let [internal-state @(rf/subscribe [::state])
        state (deep-merge initial-state internal-state)
        {:keys [title content error-content result on-close shade? open?]} state
        success? (:success? result)
        errors (if (:error result) [(:error result)] (:errors result))
        error-content (or error-content (and errors (format-errors errors)))]
    (when open?
      [modal/notification {:title [:div [status-icon success? error-content] " " title]
                           :title-class (when (or errors error-content) "alert alert-danger")
                           :content [:<> [status-text success? error-content] content]
                           :on-close (fn []
                                       (close)
                                       (when on-close (on-close)))
                           :shade? shade?}])))

(defn set-pending!
  "Globally set the modal state to reflect a pending operation.

  The given `state` is like in `status-modal` and it replaces the
  current state. It's convenient here to set the `:title` of the operation.

  The modal will be shown."
  [state]
  (rf/dispatch [::set-state state]))

(defn set-success!
  "Globally set the modal state to reflect a successful operation.

  The given `state` is like in `status-modal` and it's merged to the
  current state so state set by a previous `set-pending!` can be shared.

  The modal will be shown."
  [state]
  (rf/dispatch [::merge-state (deep-merge {:open? true
                                           :result {:success? true}}
                                          state)]))
(defn set-error!
  "Globally set the modal state to reflect a failed operation.

  The given `state` is like in `status-modal` and it's merged to the
  current state so state set by a previous `set-pending!` can be shared.

  The modal will be shown."
  [state]
  (rf/dispatch [::merge-state (deep-merge {:open? true
                                           :result {:success? false}}
                                          state)]))

(defn common-pending-handler!
  "Common variant of `set-pending!` where you wish to open the modal and
  customize the `title`."
  [title]
  (set-pending! {:open? true
                 :title title}))

(defn common-success-handler!
  "Common variant of `set-success!` where you only wish to customize the `on-close`
  by currying."
  [on-close response]
  (assert on-close)
  (if (:success response)
    (set-success! {:on-close (partial on-close response)})
    (set-error! {:result response})))

(defn common-error-handler!
  "Common variant of `set-error!` where you don't need to customize handling."
  [response]
  (set-error! {:result {:error response}}))

(defn guide
  []
  [:div
   (component-info status-modal)
   (example "status-modal while result is pending"
            [status-modal {:open? true
                           :shade? false
                           :title "Example"
                           :content [:p "We are experiencing unexpected slowness"]}])
   (example "status-modal for success"
            [status-modal {:open? true
                           :result {:success? true}
                           :shade? false
                           :title "Example"
                           :content [:p "This was a great success for all!"]}])
   (example "status-modal for result with a single error"
            [status-modal {:open? true
                           :result {:error {:status 404
                                            :status-text "Not found"}}
                           :shade? false
                           :title "Example"
                           :content [:p "This did not go as planned"]}])
   (example "status-modal for result with errors"
            [status-modal {:open? true
                           :result {:errors [{:type :t.form.validation/errors}]}
                           :shade? false
                           :title "Example"
                           :content [:p "You should check the errors"]}])])
