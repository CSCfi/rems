(ns rems.focus
  "Focuses an HTML element as soon as it exists.

  The component using this needs to acquire a reference to the element to be
  focused using React's refs, and store it in an atom as instructed in
  Reagent's documentation. [1] But instead of setting the atom directly, set it
  using the `ref-changed` function.

  When the component needs to focus an element, call `set-focus` with the atom
  which contains or will contain the element to be focused. The element will
  be focused as soon as possible, regardless of the order in which `set-focus`
  and `ref-changed` are called.

  [1] https://cljdoc.org/d/reagent/reagent/0.8.1/doc/frequently-asked-questions/how-do-i-use-react-s-refs-")

(def ^:private element-atom-to-be-focused (atom nil))

(defn- check-if-need-to-focus []
  (when-let [element-atom @element-atom-to-be-focused]
    (when-let [element @element-atom]
      (.focus element)
      (reset! element-atom-to-be-focused nil))))

(defn set-focus [element-atom]
  (reset! element-atom-to-be-focused element-atom)
  (check-if-need-to-focus))

(defn ref-changed [element-atom element]
  (reset! element-atom element)
  (check-if-need-to-focus))
