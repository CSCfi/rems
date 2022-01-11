(ns rems.administration.items
  (:require [rems.text :refer [text]])
  (:refer-clojure :exclude [remove]))

(defn add
  "Add new-item to the items with optionally an index. Adds to the end if index not given or is nil."
  ([items new-item]
   (conj (vec items) new-item))
  ([items new-item index]
   (let [index (or index (count items))]
     (vec (concat (take index items)
                  [new-item]
                  (drop index items))))))

(defn remove [items index]
  (vec (concat (subvec items 0 index)
               (subvec items (inc index)))))

(defn- swap [items index1 index2]
  (-> items
      (assoc index1 (get items index2))
      (assoc index2 (get items index1))))

(defn move-up [items index]
  (let [first-index 0
        other (max first-index (dec index))]
    (swap items index other)))

(defn move-down [items index]
  (let [last-index (dec (count items))
        other (min last-index (inc index))]
    (swap items index other)))

(defn remove-button [on-click]
  [:a.remove
   {:href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (on-click))
    :aria-label (text :t.item-lists/remove)
    :title (text :t.item-lists/remove)}
   [:i.icon-link.fas.fa-times
    {:aria-hidden true}]])

(defn move-up-button [{:keys [on-click class]}]
  [:a
   {:className (str "move-up " class)
    :href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (on-click))
    :aria-label (text :t.item-lists/move-up)
    :title (text :t.item-lists/move-up)}
   [:i.icon-link.fas.fa-chevron-up
    {:aria-hidden true}]])

(defn move-down-button [{:keys [on-click class]}]
  [:a
   {:className (str "move-down " class)
    :href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (on-click))
    :aria-label (text :t.item-lists/move-down)
    :title (text :t.item-lists/move-down)}
   [:i.icon-link.fas.fa-chevron-down
    {:aria-hidden true}]])
