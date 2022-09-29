(ns rems.util
  (:require [accountant.core :as accountant]
            [ajax.core :refer [GET PUT POST]]
            [clojure.string :as str]
            [goog.string :refer [format]]
            [re-frame.core :as rf]
            [clojure.test :refer [deftest are testing]]))

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

(defn fetch
  "Fetches data from the given url with optional map of options like #'ajax.core/GET.

  Has sensible defaults with error handler, JSON and keywords.
  You can use :custom-error-handler? to decide weather you would like use wrapper for the error handling.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.get url (clj->js opts))
  (GET url (-> (merge {:response-format :transit
                       :handler (fn [])}
                      opts
                      (if (:custom-error-handler? opts)
                        {:error-handler (:error-handler opts)}
                        {:error-handler (wrap-default-error-handler (:error-handler opts))})))))

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
              ^{:key %} [:a {:target :_blank :href (text-to-url %)} %]
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

(defn focus-when-collapse-opened
  "Focuses the given element when the (Bootstrap) collapse has opened.

   Used typically with a `ref`."
  [elem]
  (when elem
    (.on (js/$ elem)
         "shown.bs.collapse"
         (fn []
           (.focus elem)
           false))))

(defn- strip-trailing-zeroes
  [s]
  (let [without-decimal-zeroes (str/replace s #"\.[0]*$" "")
        without-trailing-zeroes (str/replace s #"[0]+$" "")]
    (if (and (= without-decimal-zeroes s)
             (str/includes? s "."))
      without-trailing-zeroes
      without-decimal-zeroes)))

(defn format-file-size
  [size]
  (when (or (zero? size) (pos? size))
    (let [[file-size type] (condp > size
                             (Math/pow 1000 2) [(/ size 1000.0) "KB"]
                             (Math/pow 1000 3) [(/ size (Math/pow 1000 2)) "MB"]
                             (Math/pow 1000 4) [(/ size (Math/pow 1000 3)) "GB"]
                             [size "B"])]
      (-> (format "%.2f" file-size)
          strip-trailing-zeroes
          (str " " type)))))

(deftest test-format-file-size
  (testing "should format sizes correctly"
    (are [expected input] (= expected (format-file-size input))
      "1 KB" 1000
      "10 KB" (* 1000 10)
      "1 GB" (* 1000 1000 1000)
      "1.55 MB" (* 1000 1000 1.55)
      "0.01 KB" (* 1000 0.012345)
      "0.02 KB" (* 1000 0.016789)
      "0 KB" 0
      nil -1
      nil nil
      nil {}
      nil [])))
