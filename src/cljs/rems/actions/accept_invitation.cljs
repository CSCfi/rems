(ns rems.actions.accept-invitation
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ token]]
   {:db (assoc db ::token token)
    ::accept-invitation token}))

(rf/reg-sub ::token (fn [db] (::token db "")))

(rf/reg-fx
 ::accept-invitation
 (fn [token]
   (let [error-handler (fn [response]
                         ((flash-message/default-error-handler :top (text :t.actions/accept-invitation))
                          response)
                         (dispatch! "#/catalogue"))]
     (post! "/api/applications/accept-invitation"
            {:url-params {:invitation-token token}
             :handler (fn [response]
                        (let [error (first (:errors response))]
                          (cond
                            (:success response)
                            (do
                              (flash-message/show-success! :top (text :t.actions/accept-invitation-success))
                              (dispatch! (str "#/application/" (:application-id response))))

                            (= :already-member (:type error))
                            (do
                              (flash-message/show-success! :top (text :t.actions/accept-invitation-already-member))
                              (dispatch! (str "#/application/" (:application-id error))))

                            (= :t.actions.errors/invalid-token (:type error))
                            (do
                              (flash-message/show-error! :top (text-format :t.actions.errors/invalid-token (:token error)))
                              (dispatch! "#/catalogue"))

                            :else (error-handler response))))
             :error-handler error-handler}))))

(defn accept-invitation-page []
  [:div
   [document-title (text :t.actions/accept-invitation)]
   [spinner/big]])
