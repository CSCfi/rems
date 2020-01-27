(ns rems.util
  (:require [accountant.core :as accountant]
            [ajax.core :refer [GET PUT POST]]
            [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [promesa.core :as p]
            [re-frame.core :as rf]))

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

(defn set-location!
  "Sets the browser URL. We use this to force a reload when e.g. the identity changes."
  [location]
  (set! (.-location js/window) location))

(defn unauthorized! []
  (rf/dispatch [:unauthorized! (.. js/window -location -href)]))

(defn redirect-when-unauthorized-or-forbidden!
  "If the request was unauthorized or forbidden, redirects the user
  to an error page and returns true. Otherwise returns false."
  [{:keys [status status-text]}]
  (let [current-url (.. js/window -location -href)]
    (case status
      401 (do
            (rf/dispatch [:unauthorized! current-url])
            true)
      403 (do
            (rf/dispatch [:forbidden! current-url])
            true)
      404 (do
            (rf/dispatch [:not-found! current-url])
            true)
      false)))

(defn- wrap-default-error-handler [handler]
  (fn [err]
    (when-not (redirect-when-unauthorized-or-forbidden! err)
      (when handler
        (handler err)))))

(defn- append-handler [old-handler new-handler]
  (fn [response]
    (try
      (when old-handler
        (old-handler response))
      (finally
       (new-handler response)))))

(defn fetch
  "Fetches data from the given url with optional map of options like #'ajax.core/GET.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks.

  Returns a promise, but it's okay to ignore it if you prefer using
  the `:handler` and `:error-handler` callbacks instead."
  [url opts]
  (js/window.rems.hooks.get url (clj->js opts))
  ;; TODO: change also put! and post! to return a promise?
  (p/create
   (fn [resolve reject]
     (GET url (-> (merge {:response-format :transit}
                         opts
                         {:error-handler (wrap-default-error-handler (:error-handler opts))})
                  (update :handler append-handler resolve)
                  (update :error-handler append-handler reject))))))

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

;; String manipulation

(defn trim-when-string [s]
  (when (string? s) (str/trim s)))

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

(def ^:private link-regex #"(?:http://|https://|www\.\w).*?(?=[^a-zA-Z0-9_/]*(?: |$))")

(defn linkify
  "Given a string, return a vector that, when concatenated, forms the
  original string, except that all substrings that resemble a link have
  been changed to hiccup links."
  [s]
  (when s
    (let [splitted (-> s
                       (str/replace link-regex #(str "\t" %1 "\t"))
                       (str/split "\t"))
          link? (fn [s] (re-matches link-regex s))
          text-to-url (fn [s] (if (re-matches #"^(http://|https://).*" s)
                                s
                                (str "http://" s)))]
      (map #(if (link? %)
              [:a {:target :_blank :href (text-to-url %)} %]
              %)
           splitted))))

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

(defn focus-when-collapse-opened [elem]
  (when elem
    (.on (js/$ elem)
         "shown.bs.collapse"
         #(.focus elem))))
