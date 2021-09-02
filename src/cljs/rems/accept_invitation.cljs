(ns rems.accept-invitation
  "Page for accepting invitations v2. See `rems.actions.accept-invitation` for the v1."
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [navigate! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ type token]]
   {::accept-invitation [type token]}))

(rf/reg-fx
 ::accept-invitation
 (fn [[type token]]
   (let [error-handler (fn [response]
                         ((flash-message/default-error-handler :top [text :t.accept-invitation/header]) response)
                         (navigate! "/catalogue"))]
     (post! "/api/invitations/accept-invitation"
            {:url-params {:token token}
             :handler (fn [response]
                        (let [error (first (:errors response))]
                          (cond
                            (:success response)
                            (do
                              (flash-message/show-success! :top [text (case type
                                                                        :workflow :t.accept-invitation.success/workflow)])
                              (case type
                                :workflow (navigate! (str "/administration/workflows/" (get-in response [:invitation/workflow :workflow/id])))))

                            (= :already-member (:type error))
                            (do
                              (flash-message/show-success! :top [text (case type
                                                                        :workflow :t.accept-invitation.errors.already-member/workflow)])
                              (case type
                                :workflow
                                (navigate! (str "/administration/workflows/" (get-in response [:invitation/workflow :workflow/id])))))

                            (= :t.actions.errors/invalid-token (:type error))
                            (do
                              (flash-message/show-error! :top [text-format :t.accept-invitation.errors/invalid-token (:token error)])
                              (navigate! "/catalogue")) ; generic front-page, user is possibly not a handler/admin

                            :else (error-handler response))))
             :error-handler error-handler}))))

(defn accept-invitation-page []
  [:div
   [document-title (text :t.accept-invitation/title)]
   [spinner/big]])
