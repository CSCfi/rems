(ns rems.email
  (:require [postal.core :as postal]
            [rems.config :refer [env]]))

(defn send-mail [to subject msg]
  (when-let [host (:smtp-host env)]
    (when-let [port (:smtp-port env)]
      (postal/send-message {:host host
                            :port port}
                           {:from "rems-admin@csc.fi"
                            :to to
                            :subject subject
                            :body msg}))))
