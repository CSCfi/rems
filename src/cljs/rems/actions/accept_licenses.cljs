(ns rems.actions.accept-licenses
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [button-wrapper]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::send-accept-licenses
 (fn [_ [_ {:keys [application-id licenses on-finished]}]]
   (let [description [text :t.actions/accept-licenses]]
     (post! "/api/applications/accept-licenses"
            {:params {:application-id application-id
                      :accepted-licenses licenses}
             :handler (flash-message/default-success-handler
                       :accept-licenses description (fn [_] (on-finished)))
             :error-handler (flash-message/default-error-handler :accept-licenses description)}))
   {}))

(defn accept-licenses-action-button [application-id licenses on-finished]
  [button-wrapper {:id "accept-licenses-button"
                   :text (text :t.actions/accept-licenses)
                   :class "btn-primary"
                   :on-click #(rf/dispatch [::send-accept-licenses {:application-id application-id
                                                                    :licenses licenses
                                                                    :on-finished on-finished}])}])
