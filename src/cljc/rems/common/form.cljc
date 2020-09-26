(ns rems.common.form
  "Common form utilities shared between UI and API.

  Includes functions for both forms and form templates."
  (:require  [clojure.string :as str]
             [clojure.test :refer [deftest is testing]]
             [com.rpl.specter :refer [ALL select transform]]
             [medley.core :refer [assoc-some find-first]]
             [rems.common.util :refer [build-index parse-int remove-empty-keys]]))

(defn supports-optional? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-placeholder? [field]
  (contains? #{:text :texta :description} (:field/type field)))

(defn supports-max-length? [field]
  (contains? #{:description :text :texta} (:field/type field)))

(defn supports-options? [field]
  (contains? #{:option :multiselect} (:field/type field)))

(defn supports-privacy? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-info-text? [field]
  (not (contains? #{:label} (:field/type field))))

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

(defn- validate-organization-field [m]
  (let [organization (:organization m)]
    (when (or (nil? organization)
              (str/blank? (:organization/id organization)))
      {:organization :t.form.validation/required})))

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
   (let [parsed (if (int? max-length) max-length (parse-int max-length))]
     (cond (nil? max-length)
           nil ; providing max-length is optional

           (nil? parsed)
           :t.form.validation/invalid-value

           (not (<= (first max-length-range) parsed (second max-length-range)))
           :t.form.validation/invalid-value))})

(defn- validate-option [option id languages]
  {id (merge (validate-text-field option :key)
             (validate-localized-text-field option :label languages))})

(defn- validate-options [options languages]
  {:field/options (apply merge (mapv #(validate-option %1 %2 languages) options (range)))})

(defn- field-option-keys [field]
  (set (map :key (:field/options field))))

(defn- validate-privacy [field fields]
  (let [privacy (get :field/privacy field :public)]
    (when-not (contains? #{:public :private} privacy)
      {:field/privacy {:privacy/type :t.form.validation/invalid-value}})))

(defn- validate-only-if-visibility [visibility fields]
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

      (not (apply distinct? (:visibility/values visibility)))
      {:field/visibility {:visibility/values :t.form.validation/invalid-value}}

      (some #(not (contains? (field-option-keys referred-field) %)) (:visibility/values visibility))
      {:field/visibility {:visibility/values :t.form.validation/invalid-value}})))

(defn- validate-visibility [field fields]
  (when-let [visibility (:field/visibility field)]
    (case (:visibility/type visibility)
      :always nil
      :only-if (validate-only-if-visibility visibility fields)
      nil {:field/visibility {:visibility/type :t.form.validation/required}}
      {:field/visibility {:visibility/type :t.form.validation/invalid-value}})))

(defn- validate-not-present [field key]
  (when (contains? field key)
    {key :unsupported}))

(defn- validate-fields [fields languages]
  (letfn [(validate-field [index field]
            {index (or (validate-field-type field)
                       (merge
                        (validate-localized-text-field field :field/title languages)
                        (if (supports-placeholder? field)
                          (validate-optional-localized-field field :field/placeholder languages)
                          (validate-not-present field :field/placeholder))
                        (if (supports-info-text? field)
                          (validate-optional-localized-field field :field/info-text languages)
                          (validate-not-present field :field/info-text))
                        (if (supports-max-length? field)
                          (validate-max-length (:field/max-length field))
                          (validate-not-present field :field/max-length))
                        (if (supports-options? field)
                          (validate-options (:field/options field) languages)
                          (validate-not-present field :field/options))
                        (if (supports-privacy? field)
                          (validate-privacy field fields)
                          (validate-not-present field :field/privacy))
                        (if (supports-visibility? field)
                          (validate-visibility field fields)
                          (validate-not-present field :field/visibility))))})]
    (apply merge (map-indexed validate-field fields))))

(defn- nil-if-empty [m]
  (when-not (empty? m)
    m))

(defn- raw-answers->formatted [raw-answers]
  (build-index {:keys [:form :field] :value-fn :value} raw-answers))

(defn validate-form-template [form languages]
  (-> (merge (validate-organization-field form)
             (validate-text-field form :form/title)
             {:form/fields (validate-fields (:form/fields form) languages)})
      remove-empty-keys
      nil-if-empty))

(defn enrich-form-answers [form current-answers-raw previous-answers-raw]
  (let [form-id (:form/id form)
        current-answers (get (raw-answers->formatted current-answers-raw) form-id)
        previous-answers (get (raw-answers->formatted previous-answers-raw) form-id)
        fields (for [field (:form/fields form)
                     :let [field-id (:field/id field)
                           current-value (get current-answers field-id)
                           previous-value (get previous-answers field-id)]]
                 (assoc-some field
                             :field/value current-value
                             :field/previous-value previous-value))]
    (if (not-empty fields)
      (assoc form :form/fields fields)
      form)))

(deftest validate-form-template-test
  (let [form {:organization {:organization/id "abc"}
              :form/title "the title"
              :form/fields [{:field/id "fld1"
                             :field/title {:en "en title"
                                           :fi "fi title"}
                            ;;  :field/info-text {:en "en info text"
                            ;;                    :fi "fi info text"}
                             :field/optional true
                             :field/type :text
                             :field/max-length "12"
                             :field/placeholder {:en "en placeholder"
                                                 :fi "fi placeholder"}}]}
        languages [:en :fi]]

    (testing "valid form"
      (is (empty? (validate-form-template form languages))))

    (testing "missing organization"
      (is (= :t.form.validation/required
             (:organization (validate-form-template (assoc-in form [:organization] nil) languages))
             (:organization (validate-form-template (assoc-in form [:organization :organization/id] "") languages)))))

    (testing "missing title"
      (is (= :t.form.validation/required
             (:form/title (validate-form-template (assoc-in form [:form/title] "") languages)))))

    (testing "zero fields is ok"
      (is (empty? (validate-form-template (assoc-in form [:form/fields] []) languages))))

    (testing "missing field title"
      (is (= {:form/fields {0 {:field/title {:en :t.form.validation/required}}}}
             (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] "") languages)
             (validate-form-template (update-in form [:form/fields 0 :field/title] dissoc :en) languages)))
      (is (= {:form/fields {0 {:field/title {:en :t.form.validation/required
                                             :fi :t.form.validation/required}}}}
             (validate-form-template (assoc-in form [:form/fields 0 :field/title] nil) languages))))

    (testing "missing field type"
      (is (= {:form/fields {0 {:field/type :t.form.validation/required}}}
             (validate-form-template (assoc-in form [:form/fields 0 :field/type] nil) languages))))

    (testing "if you use a placeholder, you must fill in all the languages"
      (is (= {:form/fields {0 {:field/placeholder {:fi :t.form.validation/required}}}}
             (validate-form-template (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder" :fi ""}) languages)
             (validate-form-template (assoc-in form [:form/fields 0 :field/placeholder] {:en "en placeholder"}) languages))))
    
    (testing "if you use info text, you must fill in all the languages"
      (is (= {:form/fields {0 {:field/info-text {:fi :t.form.validation/required}}}}
             (validate-form-template (assoc-in form [:form/fields 0 :field/info-text] {:en "en info text" :fi ""}) languages)
             (validate-form-template (assoc-in form [:form/fields 0 :field/info-text] {:en "en info text"}) languages))))

    (testing "placeholder & max-length shouldn't be present if they are not applicable"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :label)
                     (assoc-in [:form/fields 0 :field/placeholder :fi] ""))]
        (is (= {:form/fields {0 {:field/placeholder :unsupported
                                 :field/max-length :unsupported}}}
               (validate-form-template form languages)))))

    (testing "privacy, & options shouldn't be present if they are not applicable"
      (let [form (assoc form :form/fields
                        [{:field/title {:en "en" :fi "fi"}
                          :field/type :header
                          :field/privacy :invalid
                          :field/options [{:invalid-key :value}]}])]
        (is (= {:form/fields {0 {:field/privacy :unsupported
                                 :field/options :unsupported}}}
               (validate-form-template form languages)))))

    (testing "option fields"
      (let [form (assoc form :form/fields
                        [{:field/title {:en "en" :fi "fi"}
                          :field/type :option
                          :field/optional true
                          :field/options [{:key "yes"
                                           :label {:en "en yes"
                                                   :fi "fi yes"}}
                                          {:key "no"
                                           :label {:en "en no"
                                                   :fi "fi no"}}]}])]
        (testing "valid form"
          (is (empty? (validate-form-template form languages))))

        (testing "missing option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/required}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages))))

        (testing "missing option label"
          (let [empty-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= {:form/fields {0 {:field/options {0 {:label {:en :t.form.validation/required
                                                                :fi :t.form.validation/required}}}}}}
                   empty-label
                   nil-label))))))

    (testing "multiselect fields"
      (let [form (assoc form :form/fields
                        [{:field/type :multiselect
                          :field/title {:en "en" :fi "fi"}
                          :field/optional false
                          :field/options [{:key "egg"
                                           :label {:en "Egg"
                                                   :fi "Munaa"}}
                                          {:key "bacon"
                                           :label {:en "Bacon"
                                                   :fi "Pekonia"}}]}])]
        (testing "valid form"
          (is (empty? (validate-form-template form languages))))

        (testing "missing option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/required}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages))))

        (testing "missing option label"
          (let [empty-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= {:form/fields {0 {:field/options {0 {:label {:en :t.form.validation/required
                                                                :fi :t.form.validation/required}}}}}}
                   empty-label
                   nil-label))))))

    (testing "visible"
      (let [form (assoc form :form/fields
                        [{:field/id "fld1"
                          :field/title {:en "en" :fi "fi"}
                          :field/type :option
                          :field/options [{:key "yes"
                                           :label {:en "en yes"
                                                   :fi "fi yes"}}
                                          {:key "no"
                                           :label {:en "en no"
                                                   :fi "fi no"}}]}
                         {:field/id "fld2"
                          :field/title {:en "en title additional"
                                        :fi "fi title additional"}
                          :field/optional false
                          :field/type :text
                          :field/max-length "12"
                          :field/placeholder {:en "en placeholder"
                                              :fi "fi placeholder"}}])
            validate-visible (fn [visible]
                               (validate-form-template (assoc-in form [:form/fields 1 :field/visibility] visible) languages))]

        (testing "invalid type"
          (is (= {:form/fields {1 {:field/visibility {:visibility/type :t.form.validation/required}}}}
                 (validate-visible {:visibility/type nil})))
          (is (= {:form/fields {1 {:field/visibility {:visibility/type :t.form.validation/invalid-value}}}}
                 (validate-visible {:visibility/type :does-not-exist}))))

        (testing "invalid field"
          (is (= {:form/fields {1 {:field/visibility {:visibility/field :t.form.validation/required}}}}
                 (validate-visible {:visibility/type :only-if})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field nil})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {}})))
          (is (= {:form/fields {1 {:field/visibility {:visibility/field :t.form.validation/invalid-value}}}}
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "does-not-exist"}}))))

        (testing "invalid value"
          (is (= {:form/fields {1 {:field/visibility {:visibility/values :t.form.validation/required}}}}
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "fld1"}})))
          (is (= {:form/fields {1 {:field/visibility {:visibility/values :t.form.validation/invalid-value}}}}
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "fld1"}
                                    :visibility/values ["does-not-exist"]})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "fld1"}
                                    :visibility/values ["yes" "does-not-exist"]})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "fld1"}
                                    :visibility/values ["yes" "yes"]}))))

        (testing "correct data"
          (is (empty? (validate-visible {:visibility/type :always})))
          (is (empty? (validate-visible {:visibility/type :only-if
                                         :visibility/field {:field/id "fld1"}
                                         :visibility/values ["yes"]}))))))))

(defn parse-attachment-ids [string]
  (mapv parse-int (when-not (empty? string)
                    (str/split string #","))))

(defn unparse-attachment-ids [ids]
  (str/join "," ids))

(deftest test-parse-unparse-attachment-ids
  (is (= [] (parse-attachment-ids "")))
  (is (= "" (unparse-attachment-ids [])))
  (is (= [1] (parse-attachment-ids "1")))
  (is (= "1" (unparse-attachment-ids [1])))
  (is (= [1 236 3] (parse-attachment-ids "1,236,3")))
  (is (=  "1,236,3" (unparse-attachment-ids [1 236 3]))))
