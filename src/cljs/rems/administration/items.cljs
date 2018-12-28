(ns rems.administration.items
  (:refer-clojure :exclude [remove]))

(defn add [items new-item]
  (conj (vec items) new-item))

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
