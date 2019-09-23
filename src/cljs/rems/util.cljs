(ns rems.util
  (:require [accountant.core :as accountant]
            [ajax.core :refer [GET PUT POST]]
            [clojure.string :as str]
            [goog.string :refer [parseInt]]
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

(defn remove-empty-keys
  "Given a map, recursively remove keys with empty map or nil values.

  E.g., given {:a {:b {:c nil} :d {:e :f}}}, return {:a {:d {:e :f}}}."
  [m]
  (into {} (filter (fn [[_ v]] (not ((if (map? v) empty? nil?) v)))
                   (mapv (fn [[k v]] [k (if (map? v)
                                          (remove-empty-keys v)
                                          v)])
                         m))))

(defn replace-url!
  "Navigates to the given URL without adding a browser history entry."
  [url]
  (.replaceState js/window.history nil "" url)
  ;; when manipulating history, secretary won't catch the changes automatically
  (js/window.rems.hooks.navigate url)
  (accountant/dispatch-current!))

(defn navigate!
  "Navigates to the given URL."
  [url]
  (accountant/navigate! url))

(defn unauthorized! []
  (rf/dispatch [:unauthorized! (.. js/window -location -href)]))

(defn redirect-when-unauthorized-or-forbidden [{:keys [status status-text]}]
  (let [current-url (.. js/window -location -href)]
    (case status
      401 (do
            (rf/dispatch [:unauthorized! current-url])
            true)
      403 (do
            (rf/dispatch [:forbidden! current-url])
            true)
      false)))

(defn- wrap-default-error-handler [handler]
  (fn [err]
    (when-not (redirect-when-unauthorized-or-forbidden err)
      (when handler
        (handler err)))))

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

(defn parse-int [string]
  (let [parsed (parseInt string)]
    (when-not (js/isNaN parsed) parsed)))

;; String manipulation

(defn normalize-option-key
  "Strips disallowed characters from an option key"
  [key]
  (str/replace key #"\s+" ""))

(defn encode-option-keys
  "Encodes a set of option keys to a string"
  [keys]
  (->> keys
       sort
       (str/join " ")))

(defn decode-option-keys
  "Decodes a set of option keys from a string"
  [value]
  (-> value
      (str/split #"\s+")
      set
      (disj "")))

(defn linkify
  "Given a string, return a vector that, when concatenated, forms the
  original string, except that all whitespace-separated substrings that
  resemble a link have been changed to hiccup links."
  [s]
  (let [link? (fn [s] (re-matches #"^http[s]?://.*" s))]
    (map #(if (link? %) [:a {:href %} %] %)
         (interpose " " (str/split s " ")))))

(defn focus-input-field [id]
  (fn [event]
    (.preventDefault event)
    ;; focusing input fields requires JavaScript; <a href="#id"> links don't work
    (when-let [element (.getElementById js/document id)]
      (.focus element))))

(defn visibility-ratio
  "Given a DOM node, return a number from 0.0 to 1.0 describing how much of an element is inside the viewport."
  [element]
  (let [bounds (.getBoundingClientRect element)]
    (cond (<= (.-bottom bounds) 0)
          0
          (>= (.-top bounds) 0)
          1
          :else
          (/ (.-bottom bounds) (.-height bounds)))))
