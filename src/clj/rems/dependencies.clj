(ns rems.dependencies
  (:require [medley.core :refer [dissoc-in]]
            [nano-id.core :as nano-id]))

(def dependency-watchers (atom {}))

(defn watch! [deps callback]
  (let [watch-id (nano-id/nano-id)]
    (swap! dependency-watchers
           (fn [watchers]
             (reduce (fn [watchers path]
                       (assoc-in watchers path {:callback callback}))

                     watchers

                     (for [[dep ids] deps
                           id ids]
                       [dep id watch-id]))))
    watch-id))

(defn unwatch! [watch-id]
  (swap! dependency-watchers
         (fn [watchers]
           (reduce (fn [watchers path]
                     (dissoc-in watchers path))

                   watchers

                   (for [[dep xs] watchers
                         [x-id ws] xs
                         [w-id _] ws
                         :when (= w-id watch-id)]
                     [dep x-id w-id])))))

(defn notify-watchers! [deps]
  (doseq [[dep ids] deps
          id ids
          :let [watches (get-in @dependency-watchers [dep id])]
          [watch-id {:keys [callback]}] watches]
    (callback {:watch/id watch-id
               dep id})))

(comment
  (watch! {:application/id [123]} #(prn 123 %))
  (watch! {:application/id [123]} #(prn :123 %))
  (watch! {:resource/id [123]} #(prn 123 %))
  (notify-watchers! [:widget/id 456])
  (notify-watchers! [:application/id 456])
  (notify-watchers! [:application/id 123])
  (notify-watchers! {:userid "developer"})
  (unwatch! "kGyxdeGgKR8U5fuQSLYDG"))
