(ns rems.util
  (:require  [ajax.core :refer [GET PUT POST]]
             [re-frame.core :as rf]))

(defn select-vals
  "Select values in map `m` specified by given keys `ks`.

  Values will be returned in the order specified by `ks`.

  You can specify a `default-value` that will be used if the
  key is not found in the map. This is like `get` function."
  [m ks & [default-value]]
  (vec (reduce #(conj %1 (get m %2 default-value)) [] ks)))

(defn index-by
  "Index the collection coll with given keys `ks`.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn vec-dissoc [coll index]
  (vec (concat (subvec coll 0 index)
               (subvec coll (inc index)))))

(defn dispatch!
  "Dispatches to the given url."
  [url]
  (set! (.-location js/window) url))

(defn redirect-when-unauthorized [{:keys [status status-text]}]
  (when (= 401 status)
    (let [current-url (.. js/window -location -href)]
      (rf/dispatch [:unauthorized! current-url]))))

(defn- wrap-default-error-handler [handler]
  (fn [err]
    (redirect-when-unauthorized err)
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
                   :format :json
                   :response-format :transit}
                  opts)))

(defn post!
  "Dispatches a command to the given url with optional map of options like #'ajax.core/POST.

  Has sensible defaults with error handler, JSON and keywords.

  Additionally calls event hooks."
  [url opts]
  (js/window.rems.hooks.put url (clj->js opts))
  (POST url (merge {:error-handler (wrap-default-error-handler (:error-handler opts))
                    :format :json
                    :response-format :transit}
                   opts)))
