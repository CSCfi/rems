(ns rems.ajax
  (:require [ajax.core :as ajax]
            [ajax.protocols :refer [-get-response-header]]
            [clojure.string :as str]
            [rems.identity :refer [set-roles!]]))

(defn local-uri? [{:keys [uri]}]
  (not (re-find #"^\w+?://" uri)))

(defn default-headers [request]
  (if (local-uri? request)
    (-> request
        ;;(update :uri #(str js/context %))
        (update :headers #(merge {"x-csrf-token" js/csrfToken} %)))
    request))

(defn- split-words [str]
  (if (empty? str)
    nil
    (str/split str " ")))

(defn save-roles [response]
  (set-roles! (split-words (-get-response-header response "x-rems-roles")))
  response)

(defn load-interceptors! []
  (swap! ajax/default-interceptors
         conj
         (ajax/to-interceptor {:name "default headers"
                               :request default-headers})
         (ajax/to-interceptor {:name "save roles"
                               :response save-roles})))
