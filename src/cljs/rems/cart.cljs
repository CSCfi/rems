(ns rems.cart
  (:require [cljs.tools.reader.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.common.catalogue-util :refer [catalogue-items->ids]]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [get-localized-title text text-format]]
            [rems.util :refer [navigate!]]))

(rf/reg-sub
 ::cart
 (fn [db]
   (::cart db)))

(rf/reg-event-db
 ::add-item
 (fn [db [_ item]]
   (if (contains? (set (map :id (::cart db))) (:id item))
     db
     (update db ::cart conj item))))

(rf/reg-event-db
 ::remove-item
 (fn [db [_ item-id]]
   (update db ::cart #(remove (comp #{item-id} :id) %))))

(defn disable-remove-from-cart-button? [item cart-item-ids entitlement-catids]
  (when-let [children-ids (seq (map :catalogue-item/id (:children item)))]
    (and (not (contains? (set entitlement-catids) (:id item)))
         (some (set cart-item-ids) children-ids))))

(deftest test-disable-remove-from-cart-button
  (testing "without hierarchy"
    (is (nil? (disable-remove-from-cart-button? {:id 1} [1] nil))
        "can remove item")
    (is (nil? (disable-remove-from-cart-button? {:id 1} [1 2 3] nil))
        "can remove item"))
  (testing "with item and children in cart"
    (is (disable-remove-from-cart-button? {:id 2 :children [{:catalogue-item/id 3}]} [2 3] nil)
        "cannot remove parent")
    (is (nil? (disable-remove-from-cart-button? {:id 3 :part-of {:catalogue-item/id 2}} [2 3] nil))
        "can remove child"))
  (testing "with parent in cart"
    (is (nil? (disable-remove-from-cart-button? {:id 2 :children [{:catalogue-item/id 3}]} [2] nil))
        "can remove item"))
  (testing "with item and children in cart, and previous entitlement to item"
    (is (false? (disable-remove-from-cart-button? {:id 2 :children [{:catalogue-item/id 3}]} [2 3] [2]))
        "can remove item")))

(defn disable-add-to-cart-button? [item cart-item-ids entitlement-catids]
  (when-let [parent-id (-> item :part-of :catalogue-item/id)]
    (not (contains? (set/union (set cart-item-ids) (set entitlement-catids))
                    parent-id))))

(deftest test-disable-add-to-cart-button
  (testing "without hierarchy"
    (is (nil? (disable-add-to-cart-button? {:id 1} nil nil))
        "can add to cart"))
  (testing "with parent, and unrelated item in cart"
    (is (nil? (disable-add-to-cart-button? {:id 2 :children [{:catalogue-item/id 3}]} [1] nil))
        "can add to cart"))
  (testing "with child, without parent in cart"
    (is (true? (disable-add-to-cart-button? {:id 3 :part-of {:catalogue-item/id 2}} [1] nil))
        "cannot add to cart"))
  (testing "with child, and parent in cart"
    (is (false? (disable-add-to-cart-button? {:id 3 :part-of {:catalogue-item/id 2}} [2] nil))
        "can add to cart"))
  (testing "with child, without parent in cart, with entitlement to parent"
    (is (false? (disable-add-to-cart-button? {:id 3 :part-of {:catalogue-item/id 2}} [1] [2]))
        "can add to cart")))

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  ([item]
   (add-to-cart-button item nil))
  ([item disabled?]
   [:button.btn.btn-primary.add-to-cart
    {:type :button
     :disabled disabled?
     :on-click #(rf/dispatch [::add-item item])
     :aria-label (str (text :t.cart/add) ": " (get-localized-title item))}
    (text :t.cart/add)]))

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  ([item]
   (remove-from-cart-button item nil))
  ([item disabled?]
   [:button.btn.btn-secondary.remove-from-cart
    {:type :button
     :disabled disabled?
     :on-click #(rf/dispatch [::remove-item (:id item)])
     :aria-label (str (text :t.cart/remove)
                      ": "
                      (get-localized-title item))}
    (text :t.cart/remove)]))

;; TODO make util for other pages to use?
(defn parse-items [items-string]
  (->> (str/split items-string #",")
       (mapv edn/read-string)))

(defn- apply-button [items]
  (let [item-ids (sort (catalogue-items->ids items))]
    [atoms/rate-limited-action-button
     {:id :apply-for-catalogue-items-button
      :class "btn-primary apply-for-catalogue-items"
      :on-click (fn [] (navigate! (str "/application?items=" (str/join "," item-ids))))
      :label (text :t.cart/apply)
      :aria-label (str (text :t.cart/apply)
                       ": "
                       (->> items
                            (map #(get-localized-title %))
                            (str/join ", ")))}]))

(defn- item-view
  ([item apply-button?]
   (item-view apply-button? item nil))
  ([item apply-button? disable-remove-button?]
   (let [title (get-localized-title item)
         td-elem (if (:part-of item)
                   :td.title.child
                   :td.title)]
     [:tr.cart-item
      [td-elem title]
      [:td.commands
       [:div.commands.justify-content-end
        [remove-from-cart-button item disable-remove-button?]
        (when apply-button? [apply-button [item]])]]])))

(defn sort-items [items]
  (->> items
       (group-by #(or (:catalogue-item/id (:part-of %)) (:id %)))
       (map (fn [[_id group]]
              (let [[parent not-parents] ((juxt filter remove) :children group)]
                (concat parent (sort-by get-localized-title not-parents)))))
       (sort-by (comp get-localized-title first))
       (into [] cat)))

(deftest test-sort-items
  (let [items [{:id 17 :localizations {:en {:title "B"}} :part-of {:catalogue-item/id 18}}
               {:id 18 :localizations {:en {:title "C"}} :children [{:catalogue-item/id 17}]}
               {:id 19 :localizations {:en {:title "A"}}}
               {:id 1 :localizations {:en {:title "Z"}} :children [{:catalogue-item/id 20}]}
               {:id 2 :localizations {:en {:title "F"}} :children [{:catalogue-item/id 3}
                                                                   {:catalogue-item/id 4}]}
               {:id 3 :localizations {:en {:title "E"}} :part-of {:catalogue-item/id 2}}
               {:id 4 :localizations {:en {:title "D"}} :part-of {:catalogue-item/id 2}}
               {:id 20 :localizations {:en {:title "G"}} :part-of {:catalogue-item/id 1}}]
        items-without-hierarchy [{:id 1 :localizations {:en {:title "Z"}}}
                                 {:id 2 :localizations {:en {:title "F"}}}
                                 {:id 3 :localizations {:en {:title "O"}}}
                                 {:id 4 :localizations {:en {:title "V"}}}]
        ->id-and-title (map (fn [item]
                              {:id (:id item)
                               :title (-> item :localizations :en :title)}))]
    (testing "top-level item is first"
      (is (= [{:id 19 :title "A"} ; not a part of any hierarchy, sorted alphabetically, A < B...Ö
              {:id 18 :title "C"} ; parent of 17, sorted before it
              {:id 17 :title "B"} ; child of 18, sorted after
              {:id 2 :title "F"}  ; parent of 3, 4
              {:id 4 :title "D"}  ; child of 2, D < E
              {:id 3 :title "E"}  ; child of 2, D < E
              {:id 1 :title "Z"}  ; parent of 20
              {:id 20 :title "G"}]
             (into [] ->id-and-title (sort-items items)))))

    (testing "without hierarchy, items are sorted alphabetically"
      (is (= [{:id 2, :title "F"}
              {:id 3, :title "O"}
              {:id 4, :title "V"}
              {:id 1, :title "Z"}]
             (into [] ->id-and-title (sort-items items-without-hierarchy)))))))

(defn- bundle-view
  ([items]
   (bundle-view items nil))
  ([items entitlements]
   (let [item-count (count items)
         many-items? (< 1 item-count)
         disable-remove-button-for-item? (fn [item]
                                           (and many-items?
                                                (disable-remove-from-cart-button? item (catalogue-items->ids items) entitlements)))]
     (into [:tbody.cart-bundle]
           (concat
            (map (fn [item]
                   [item-view item (not many-items?) (disable-remove-button-for-item? item)])
                 (sort-items items))
            (when many-items?
              [[:tr [:td.commands.text-right {:col-span 2}
                     (text-format :t.cart/apply-for-bundle item-count)
                     [:span.mr-3]
                     [apply-button items]]]]))))))

(defn cart-list
  "List of shopping cart items"
  ([items]
   (cart-list items nil))
  ([items entitlements]
   [:div.mt-5
    (text :t.cart/intro)
    [:div.outer-cart.mb-3
     [:div.inner-cart
      [:div.cart-title.pt-3.h5 {:role "status"
                                :aria-live "polite"
                                :aria-atomic true}
       [:i.fa.fa-shopping-cart]
       [:span (text-format :t.cart/header (count items))]]
      (into [:table.rems-table.cart {:id :cart}]
            (for [group (vals (into (sorted-map)
                                    (group-by :wfid items)))]
              [bundle-view group entitlements]))]]]))

(defn cart-list-container [entitlements]
  [cart-list @(rf/subscribe [::cart]) entitlements])

(defn guide []
  [:div
   (component-info item-view)
   (example "item-view, single"
            [:code
             [:table.rems-table.cart
              [:tbody
               [item-view {:localizations {:en {:title "Item title"}}} true]]]])
   (example "item-view, one of many has no apply button"
            [:code
             [:table.rems-table.cart
              [:tbody
               [item-view {:localizations {:en {:title "Item title"}}} false]]]])

   (component-info bundle-view)
   (example "bundle-view"
            [:code
             [:table.rems-table.cart
              [bundle-view [{:localizations {:en {:title "Item title 1"}}}
                            {:localizations {:en {:title "Item title 2"}}}
                            {:localizations {:en {:title "Item title 3"}}}]]]])

   (component-info cart-list)
   (example "cart-list empty"
            [cart-list [] nil])
   (example "cart-list with two items of different workflow"
            [cart-list [{:localizations {:en {:title "Item title"}} :wfid 1}
                        {:localizations {:en {:title "Another title"}} :wfid 2}]])
   (example "cart-list with three items of same workflow and two of different"
            [cart-list [{:localizations {:en {:title "First title"}} :wfid 2}
                        {:localizations {:en {:title "Second title"}} :wfid 1}
                        {:localizations {:en {:title "Third title"}} :wfid 1}
                        {:localizations {:en {:title "Fourth title"}} :wfid 1}
                        {:localizations {:en {:title "Fifth title"}} :wfid 3}]])
   (example "cart-list with five items of same workflow but of two different forms"
            [cart-list [{:localizations {:en {:title "First form"}} :wfid 1 :formid 1}
                        {:localizations {:en {:title "Second form"}} :wfid 1 :formid 2}
                        {:localizations {:en {:title "First form"}} :wfid 1 :formid 1}
                        {:localizations {:en {:title "Second form"}} :wfid 1 :formid 2}
                        {:localizations {:en {:title "First form"}} :wfid 1 :formid 1}]])
   (example "cart-list with hierarchical items"
            [cart-list [{:id 1 :localizations {:en {:title "Top-level item"}} :wfid 1 :formid 1 :children [{:catalogue-item/id 2}]}
                        {:id 2 :localizations {:en {:title "Complementary item 1"}} :wfid 1 :formid 1 :part-of {:catalogue-item/id 1}}]])])
