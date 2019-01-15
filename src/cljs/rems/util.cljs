(ns rems.util
  (:require [ajax.core :refer [GET PUT POST]]
            [re-frame.core :as rf]
            [secretary.core :as secretary]))

;; TODO move to cljc
(defn getx
  "Like `get` but throws an exception if the key is not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like `get-in` but throws an exception if the key is not found."
  [m ks]
  (reduce getx m ks))

(defn dispatch!
  "Dispatches to the given url.

  If `replace?` is given, then browser history is replaced and not pushed."
  ([url]
   (dispatch! url false))
  ([url replace?]
   (if replace?
     (do
       ;; when manipulating history,
       ;;secretary won't catch the changes automatically
       (.replaceState (.-history js/window) nil url url)
       (js/window.rems.hooks.navigate url)
       (secretary/dispatch! url))
     (set! (.-location js/window) url))))

(defn redirect-when-unauthorized-or-forbidden [{:keys [status status-text]}]
  (let [current-url (.. js/window -location -href)]
    (case status
      401 (rf/dispatch [:unauthorized! current-url])
      403 (rf/dispatch [:forbidden! current-url])
      nil)))

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
  (GET url (merge {:response-format :transit}
                  opts
                  {:error-handler (wrap-default-error-handler (:error-handler opts))})))

(defn put!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/PUT.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (PUT url (merge {:format :transit
                   :response-format :transit}
                  opts
                  {:error-handler (wrap-default-error-handler (:error-handler opts))})))

(defn post!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/POST.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (POST url (merge {:format :transit
                    :response-format :transit}
                   opts
                   {:error-handler (wrap-default-error-handler (:error-handler opts))})))
