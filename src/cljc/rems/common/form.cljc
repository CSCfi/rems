(ns rems.common.form
  (:require  [clojure.string :as str]
             [clojure.test :refer [deftest is testing]]
             [medley.core :refer [find-first]]
             [rems.common-util :refer [getx-in parse-int remove-empty-keys]]))

(defn supports-optional? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-placeholder? [field]
  (contains? #{:text :texta :description} (:field/type field)))

(defn supports-max-length? [field]
  (contains? #{:description :text :texta} (:field/type field)))

(defn supports-options? [field]
  (contains? #{:option :multiselect} (:field/type field)))

(defn supports-visibility? [field]
  true) ; at the moment all field types

(defn- generate-field-ids
  "Generate a set of unique field ids taking into account what have been given already.

  Returns in the format [{:field/id \"fld1\"} ...], same as the fields."
  [fields]
  (let [generated-ids (map #(str "fld" %) (iterate inc 1))
        default-ids (for [id (->> generated-ids
                                  (remove (set (map :field/id fields))))]
                      {:field/id id})]
    default-ids))

(def generate-field-id
  "Generate a single unique field id taking into account what have been given already.

  Returns in the format [{:field/id \"fld1\"} ...], same as the fields."
  (comp first generate-field-ids))

(defn assign-field-ids
  "Go through the given fields and assign each a unique `:field/id` if it's missing."
  [fields]
  (mapv merge (generate-field-ids fields) fields))

(deftest test-assign-field-ids
  (is (= [] (assign-field-ids [])))
  (is (= [{:field/id "fld1"} {:field/id "fld2"}] (assign-field-ids [{} {}])))
  (is (= [{:field/id "abc"}] (assign-field-ids [{:field/id "abc"}])))
  (is (= [{:field/id "abc"} {:field/id "fld2"}] (assign-field-ids [{:field/id "abc"} {}])))
  (is (= [{:field/id "fld2"} {:field/id "fld3"}] (assign-field-ids [{:field/id "fld2"} {}])))
  (is (= [{:field/id "fld2"} {:field/id "fld1"}] (assign-field-ids [{} {:field/id "fld1"}])))
  (is (= [{:field/id "fld2"} {:field/id "fld4"} {:field/id "fld3"}] (assign-field-ids [{:field/id "fld2"} {} {:field/id "fld3"}]))))

(defn field-visible? [field values]
  (let [visibility (:field/visibility field)]
    (or (nil? visibility)
        (= :always (:visibility/type visibility))
        (and (= :only-if (:visibility/type visibility))
             (contains? (set (:visibility/values visibility))
                        (get values (:field/id (:visibility/field visibility))))))))

(deftest test-field-visible?
  (is (true? (field-visible? nil nil)))
  (is (true? (field-visible? {:field/visibility {:visibility/type :always}}
                             nil)))
  (is (false? (field-visible? {:field/visibility {:visibility/type :only-if
                                                  :visibility/field {:field/id "1"}
                                                  :visibility/values ["yes"]}}
                              nil)))
  (is (false? (field-visible? {:field/visibility {:visibility/type :only-if
                                                  :visibility/field {:field/id "1"}
                                                  :visibility/values ["yes"]}}
                              {"1" "no"})))
  (is (true? (field-visible? {:field/visibility {:visibility/type :only-if
                                                 :visibility/field {:field/id "1"}
                                                 :visibility/values ["yes"]}}
                             {"1" "yes"})))
  (is (true? (field-visible? {:field/visibility {:visibility/type :only-if
                                                 :visibility/field {:field/id "1"}
                                                 :visibility/values ["yes" "definitely"]}}
                             {"1" "definitely"}))))

(defn- validate-text-field [m key]
  (when (str/blank? (get m key))
    {key :t.form.validation/required}))

(def field-types #{:attachment :date :description :email :header :label :multiselect :option :text :texta})

(defn- validate-field-type [m]
  (let [type (keyword (get m :field/type))]
    (cond
      (not type)
      {:field/type :t.form.validation/required}

      (not (contains? field-types type))
      {:field/type :t.form.validation/invalid-value})))

(defn- validate-localized-text-field [m key languages]
  {key (apply merge (mapv #(validate-text-field (get m key) %) languages))})

(defn- validate-optional-localized-field [m key languages]
  (let [validated (mapv #(validate-text-field (get m key) %) languages)]
    ;; partial translations are not allowed
    (when (not-empty (remove identity validated))
      {key (apply merge validated)})))

(def ^:private max-length-range [0 32767])

(defn- validate-max-length [max-length]
  {:field/max-length
   (cond (nil? max-length)
         nil

         (int? max-length)
         (when-not (<= (first max-length-range) max-length (second max-length-range))
           :t.form.validation/invalid-value)

         (not (string? max-length))
         :t.form.validation/invalid-value

         (str/blank? max-length)
         :t.form.validation/invalid-value

         :else
         (let [parsed (parse-int max-length)]
           (when (or (nil? parsed)
                     (not (<= (first max-length-range) parsed (second max-length-range))))
             :t.form.validation/invalid-value)))})

(defn- validate-option [option id languages]
  {id (merge (validate-text-field option :key)
             (validate-localized-text-field option :label languages))})

(defn- validate-options [options languages]
  {:field/options (apply merge (mapv #(validate-option %1 %2 languages) options (range)))})

(defn- field-option-keys [field]
  (set (map :key (:field/options field))))

(defn- validate-only-if-field [field visibility fields]
  (let [referred-id (get-in visibility [:visibility/field :field/id])
        referred-field (find-first (comp #{referred-id} :field/id) fields)]
    (cond
      (not (:visibility/field visibility))
      {:field/visibility {:visibility/field :t.form.validation/required}}

      (empty? referred-id)
      {:field/visibility {:visibility/field :t.form.validation/required}}

      (not referred-field)
      {:field/visibility {:visibility/field :t.form.validation/invalid-value}}

      (not (supports-options? referred-field))
      {:field/visibility {:visibility/field :t.form.validation/invalid-value}}

      (empty? (:visibility/values visibility))
      {:field/visibility {:visibility/values :t.form.validation/required}}

      (some #(not (contains? (field-option-keys referred-field) %)) (:visibility/values visibility))
      {:field/visibility {:visibility/values :t.form.validation/invalid-value}})))

(defn- validate-visibility [field fields]
  (when-let [visibility (:field/visibility field)]
    (case (:visibility/type visibility)
      :always nil
      :only-if (validate-only-if-field field visibility fields)
      nil {:field/visibility {:visibility/type :t.form.validation/required}}
      {:field/visibility {:visibility/type :t.form.validation/invalid-value}})))

(defn- validate-fields [fields languages]
  (letfn [(validate-field [index field]
            {index (merge (validate-field-type field)
                          (validate-localized-text-field field :field/title languages)
                          (when (supports-placeholder? field)
                            (validate-optional-localized-field field :field/placeholder languages))
                          (when (supports-max-length? field)
                            (validate-max-length (:field/max-length field)))
                          (when (supports-options? field)
                            (validate-options (:field/options field) languages))
                          (when (supports-visibility? field)
                            (validate-visibility field fields)))})]
    (apply merge (map-indexed validate-field fields))))

(defn- nil-if-empty [m]
  (when-not (empty? m)
    m))

(defn validate-form [form languages]
  (-> (merge (validate-text-field form :form/organization)
             (validate-text-field form :form/title)
             {:form/fields (validate-fields (:form/fields form) languages)})
      remove-empty-keys
      nil-if-empty))

(deftest validate-form-test
  (let [form {:form/organization "abc"
              :form/title "the title"
              :form/fields [{:field/id "fld1"
                             :field/title {:en "en title"
                                           :fi "fi title"}
                             :field/optional true
                             :field/type :text
                             :field/max-length "12"
                             :field/placeholder {:en "en placeholder"
                                                 :fi "fi placeholder"}}]}
        languages [:en :fi]]

    (testing "valid form"
      (is (empty? (validate-form form languages))))

    (testing "missing organization"
      (is (= (:form/organization (validate-form (assoc-in form [:form/organization] "") languages))
             :t.form.validation/required)))

    (testing "missing title"
      (is (= (:form/title (validate-form (assoc-in form [:form/title] "") languages))
             :t.form.validation/required)))

    (testing "zero fields is ok"
      (is (empty? (validate-form (assoc-in form [:form/fields] []) languages))))

    (testing "missing field title"
      (let [nil-title (validate-form (assoc-in form [:form/fields 0 :field/title] nil) languages)]
        (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/title :en] "") languages)
                       [:form/fields 0 :field/title :en])
               (get-in (validate-form (update-in form [:form/fields 0 :field/title] dissoc :en) languages)
                       [:form/fields 0 :field/title :en])
               (get-in nil-title [:form/fields 0 :field/title :en])
               (get-in nil-title [:form/fields 0 :field/title :fi])
               :t.form.validation/required))))

    (testing "missing field type"
      (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/type] nil) languages)
                     [:form/fields 0 :field/type])
             :t.form.validation/required)))

    (testing "if you use a placeholder, you must fill in all the languages"
      (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder" :fi ""}) languages)
                     [:form/fields 0 :field/placeholder :fi])
             (get-in (validate-form (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder"}) languages)
                     [:form/fields 0 :field/placeholder :fi])
             :t.form.validation/required)))

    (testing "placeholder is not validated if it is not used"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :label)
                     (assoc-in [:form/fields 0 :field/placeholder :fi] ""))]
        (is (empty? (validate-form form languages)))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                         [:form/fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages)
                         [:form/fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "... are not validated when options are not used"
          (let [form (-> form
                         (assoc-in [:form/fields 0 :field/options 0 :key] "")
                         (assoc-in [:form/fields 0 :field/type] :texta))]
            (is (empty? (validate-form form languages)))))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:form/fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :multiselect)
                     (assoc-in [:form/fields 0 :field/options] [{:key "egg"
                                                                 :label {:en "Egg"
                                                                         :fi "Munaa"}}
                                                                {:key "bacon"
                                                                 :label {:en "Bacon"
                                                                         :fi "Pekonia"}}]))]
        (testing "valid form"
          (is (empty? (validate-form form languages))))

        (testing "missing option key"
          (is (= (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                         [:form/fields 0 :field/options 0 :key])
                 (get-in (validate-form (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages)
                         [:form/fields 0 :field/options 0 :key])
                 :t.form.validation/required)))

        (testing "missing option label"
          (let [empty-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= (get-in empty-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in empty-label [:form/fields 0 :field/options 0 :label :fi])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :en])
                   (get-in nil-label [:form/fields 0 :field/options 0 :label :fi])
                   :t.form.validation/required))))))

    (testing "visible"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/id] "fld1")
                     (assoc-in [:form/fields 0 :field/type] :option)
                     (assoc-in [:form/fields 0 :field/options] [{:key "yes"
                                                                 :label {:en "en yes"
                                                                         :fi "fi yes"}}
                                                                {:key "no"
                                                                 :label {:en "en no"
                                                                         :fi "fi no"}}])
                     (assoc-in [:form/fields 1] {:field/id "fld2"
                                                 :field/title {:en "en title additional"
                                                               :fi "fi title additional"}
                                                 :field/optional false
                                                 :field/type :text
                                                 :field/max-length "12"
                                                 :field/placeholder {:en "en placeholder"
                                                                     :fi "fi placeholder"}}))
            validate-visible (fn [visible]
                               (validate-form (assoc-in form [:form/fields 1 :field/visibility] visible) languages))]

        (testing "invalid type"
          (is (= (getx-in (validate-visible {:visibility/type nil})
                          [:form/fields 1 :field/visibility :visibility/type])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visibility/type :does-not-exist})
                          [:form/fields 1 :field/visibility :visibility/type])
                 :t.form.validation/invalid-value)))

        (testing "invalid field"
          (is (= (getx-in (validate-visible {:visibility/type :only-if})
                          [:form/fields 1 :field/visibility :visibility/field])
                 (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field nil})
                          [:form/fields 1 :field/visibility :visibility/field])
                 (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field {}})
                          [:form/fields 1 :field/visibility :visibility/field])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field {:field/id "does-not-exist"}})
                          [:form/fields 1 :field/visibility :visibility/field])
                 :t.form.validation/invalid-value)))

        (testing "invalid value"
          (is (= (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field {:field/id "fld1"}})
                          [:form/fields 1 :field/visibility :visibility/values])
                 :t.form.validation/required))
          (is (= (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field {:field/id "fld1"}
                                             :visibility/values ["does-not-exist"]})
                          [:form/fields 1 :field/visibility :visibility/values])
                 (getx-in (validate-visible {:visibility/type :only-if
                                             :visibility/field {:field/id "fld1"}
                                             :visibility/values ["yes" "does-not-exist"]})
                          [:form/fields 1 :field/visibility :visibility/values])
                 :t.form.validation/invalid-value)))

        (testing "correct data"
          (is (empty? (validate-visible {:visibility/type :always})))
          (is (empty? (validate-visible {:visibility/type :only-if
                                         :visibility/field {:field/id "fld1"}
                                         :visibility/values ["yes"]}))))))))

