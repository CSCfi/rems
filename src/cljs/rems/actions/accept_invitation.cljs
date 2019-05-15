(ns rems.actions.accept-invitation
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ token]]
   (status-modal/common-pending-handler! (text :t.actions/accept-invitation))
   {:db (assoc db ::token token)
    ::accept-invitation token}))

(rf/reg-sub ::token (fn [db] (::token db "")))

(defn- errors-to-content [errors]
  [:div (for [{:keys [type token]} errors]
          [:p
           (case type
             :t.actions.errors/invalid-token (text-format :t.actions.errors/invalid-token token)
             (text type))])])

(defn- error-handler [response]
  (status-modal/set-error!
   (merge {:on-close #(dispatch! "#/catalogue")}
          (if (:error response)
            {:result {:error response}}
            {:error-content (errors-to-content (:errors response))}))))

(defn- success-handler [response]
  (cond (:success response)
        (status-modal/set-success! {:content (text :t.actions/accept-invitation-success)
                                    :on-close #(dispatch! (str "#/application/" (:application-id response)))})

        (= :already-member (:type (first (:errors response))))
        (status-modal/set-success! {:content (text :t.actions/accept-invitation-already-member)
                                    :on-close #(dispatch! (str "#/application/" (:application-id (first (:errors response)))))})

        :else (error-handler response)))

(rf/reg-fx
 ::accept-invitation
 (fn [token]
   (post! "/api/applications/accept-invitation"
          {:url-params {:invitation-token token}
           :handler success-handler
           :error-handler error-handler})))

(defn accept-invitation-page []
  [:div
   [document-title (text :t.actions/accept-invitation)]
   [spinner/big]])
