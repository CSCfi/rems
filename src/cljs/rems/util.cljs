(ns rems.util
  (:require  [ajax.core :refer [GET PUT POST]]
             [re-frame.core :as rf]))

(defn dispatch!
  "Dispatches to the given url."
  [url]
  (set! (.-location js/window) url))

(defn redirect-when-unauthorized-or-forbidden [{:keys [status status-text]}]
  (let [current-url (.. js/window -location -href)]
    (case status
      401 (rf/dispatch [:unauthorized! current-url])
      403 (rf/dispatch [:forbidden! current-url]))))

(defn- wrap-default-error-handler [handler]
  (fn [err]
    (redirect-when-unauthorized-or-forbidden err)
    (when handler (handler err))))

(defn fetch
  "Fetches data from the given url with optional map of options like #'ajax.core/GET.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.get url (clj->js opts))
  (GET url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                   :response-format :transit}
                  opts)))

(defn put!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/PUT.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (PUT url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                   :format :transit
                   :response-format :transit}
                  opts)))

(defn post!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/POST.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (POST url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                    :format :transit
                    :response-format :transit}
                   opts)))
