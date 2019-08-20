(ns rems.actions.accept-licenses
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::send-accept-licenses
 (fn [_ [_ {:keys [application-id licenses on-finished]}]]
   (post! "/api/applications/accept-licenses"
          {:params {:application-id application-id
                    :accepted-licenses licenses}
           :handler (fn [_] (on-finished))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn accept-licenses-action-button [application-id licenses on-finished]
  [button-wrapper {:id "accept-licenses-button"
                   :text (text :t.actions/accept-licenses)
                   :class "btn-primary"
                   :on-click #(rf/dispatch [::send-accept-licenses {:application-id application-id
                                                                    :licenses licenses
                                                                    :on-finished on-finished}])}])
