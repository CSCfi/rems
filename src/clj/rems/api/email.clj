(ns rems.api.email
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [extended-logging]] ; required for route :roles
            [rems.email.core :as email]
            [ring.util.http-response :refer :all]))

(def email-api
  (context "/email" []
    :tags ["email"]

    (POST "/send-reminders" request
      :summary "Send all reminders."
      :roles #{:api-key}
      (extended-logging request)
      (email/generate-handler-reminder-emails!)
      (email/generate-reviewer-reminder-emails!)
      (ok "OK"))

    (POST "/send-handler-reminder" request
      :summary "Send reminders about open applications to all handlers."
      :roles #{:api-key}
      (extended-logging request)
      (email/generate-handler-reminder-emails!)
      (ok "OK"))

    (POST "/send-reviewer-reminder" request
      :summary "Send reminders about applications pending review."
      :roles #{:api-key}
      (extended-logging request)
      (email/generate-reviewer-reminder-emails!)
      (ok "OK"))))
