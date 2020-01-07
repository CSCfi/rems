(ns rems.api.email
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.email.core :as email]
            [ring.util.http-response :refer :all]))

(def email-api
  (context "/email" []
    :tags ["email"]

    (POST "/send-reminders" []
      :summary "Send all reminders."
      :roles #{:api-key}
      (email/generate-handler-reminder-emails!)
      (email/generate-reviewer-reminder-emails!)
      (ok "OK"))

    (POST "/send-handler-reminder" []
      :summary "Send reminders about open applications to all handlers."
      :roles #{:api-key}
      (email/generate-handler-reminder-emails!)
      (ok "OK"))

    (POST "/send-reviewer-reminder" []
      :summary "Send reminders about applications pending review."
      :roles #{:api-key}
      (email/generate-reviewer-reminder-emails!)
      (ok "OK"))))
