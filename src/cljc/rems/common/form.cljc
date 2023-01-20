(ns rems.common.form
  "Common form utilities shared between UI and API.

  Includes functions for both forms and form templates."
  (:require  [clojure.string :as str]
             [clojure.test :refer [deftest is testing]]
             [com.rpl.specter :refer [ALL transform]]
             [medley.core :refer [assoc-some find-first]]
             [rems.common.util :refer [build-index parse-int remove-empty-keys]]))

;;; Field values

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

;; TODO for historical reasons we separate multiselect values with a
;; space, and attachments with a comma.

(defn unparse-multiselect-values
  "Encodes a set of option keys to a string"
  [keys]
  (->> keys
       sort
       (str/join " ")))

(defn parse-multiselect-values
  "Decodes a set of option keys from a string"
  [value]
  (if value
    (-> value
        (str/split #"\s+")
        set
        (disj ""))
    #{}))

(deftest test-parse-unparse-multiselect-values
  (is (= #{} (parse-multiselect-values "")))
  (is (= #{} (parse-multiselect-values nil)))
  (is (= "" (unparse-multiselect-values nil)))
  (is (= "" (unparse-multiselect-values #{})))
  (is (= #{"yes"} (parse-multiselect-values "yes")))
  (is (= "yes" (unparse-multiselect-values #{"yes"})))
  (is (= #{"yes" "maybe" "no"} (parse-multiselect-values "yes maybe no")))
  (is (= "maybe no yes" (unparse-multiselect-values #{"yes" "maybe" "no"}))))

(defn- raw-answers->formatted [raw-answers]
  (build-index {:keys [:form :field] :value-fn :value} raw-answers))

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

(defn add-default-field-value [field]
  (assoc field :field/value
         (case (:field/type field)
           :table []
           ;; default
           "")))

(defn add-default-values [form]
  (transform [:form/fields ALL] add-default-field-value form))

;;; Field types and their features

(defn supports-optional? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-placeholder? [field]
  (contains? #{:text :texta :description :phone-number :ip-address} (:field/type field)))

(defn supports-max-length? [field]
  (contains? #{:description :text :texta} (:field/type field)))

(defn supports-options? [field]
  (contains? #{:option :multiselect} (:field/type field)))

(defn supports-privacy? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-info-text? [field]
  (not (contains? #{:label :header} (:field/type field))))

(defn supports-visibility? [field]
  true) ; at the moment all field types

(defn supports-columns? [field]
  (= :table (:field/type field)))

;;; Field ids

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

;;; Visibility

(defn field-depends-on-field [field]
  (get-in field [:field/visibility :visibility/field :field/id]))

(defn field-visible? [field field-values]
  (let [visibility (:field/visibility field)
        values (->> (field-depends-on-field field)
                    (get field-values)
                    ;; NB! by happy coincidence unparse-multiselect-values also for option fields
                    parse-multiselect-values)]
    (or (nil? visibility)
        (= :always (:visibility/type visibility))
        (and (= :only-if (:visibility/type visibility))
             (some? (some (set (:visibility/values visibility)) values))))))

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
                             {"1" "definitely"})))
  (testing "multiselect field values"
    (is (true? (field-visible? {:field/visibility {:visibility/type :only-if
                                                   :visibility/field {:field/id "1"}
                                                   :visibility/values ["yes" "definitely"]}}
                               {"1" "definitely maybe"})))
    (is (false? (field-visible? {:field/visibility {:visibility/type :only-if
                                                    :visibility/field {:field/id "1"}
                                                    :visibility/values ["yes" "no"]}}
                                {"1" "definitely maybe"})))))

(defn enrich-form-field-visible [form]
  (let [field-values (build-index {:keys [:field/id] :value-fn :field/value} (:form/fields form))
        update-field-visibility (fn [field] (assoc field :field/visible (field-visible? field field-values)))]
    (transform [:form/fields ALL]
               update-field-visibility
               form)))

;;; Validating form templates

(defn- validate-text-field [m key]
  (when (str/blank? (get m key))
    {key :t.form.validation/required}))

(defn- validate-organization-field [m]
  (let [organization (:organization m)]
    (when (or (nil? organization)
              (str/blank? (:organization/id organization)))
      {:organization :t.form.validation/required})))

(def field-types #{:attachment :date :description :email :header :ip-address :label :multiselect :phone-number :option :text :texta :table})

(defn- validate-field-type [m]
  (let [type (keyword (get m :field/type))]
    (cond
      (not type)
      {:field/type :t.form.validation/required}

      (not (contains? field-types type))
      {:field/type :t.form.validation/invalid-value})))

(defn- validate-localized-text-field [m key languages]
  {key (apply merge (mapv #(validate-text-field (get m key) %) languages))})

(deftest test-validate-localized-text-field
  (is (= {:foo nil}
         (validate-localized-text-field {:foo {:fi "FI" :en "EN"}} :foo [:fi :en])))
  (is (= {:foo {:fi :t.form.validation/required
                :en :t.form.validation/required}}
         (validate-localized-text-field {:foo {:fi "" :en ""}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-localized-text-field {:foo {:fi "FI"}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-localized-text-field {:foo {:fi "FI" :en ""}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-localized-text-field {:foo {:fi "FI" :en " "}} :foo [:fi :en]))))

(defn- validate-optional-localized-field [m key languages]
  (let [validated (mapv #(validate-text-field (get m key) %) languages)]
    ;; partial translations are not allowed
    (when (some nil? validated)
      {key (apply merge validated)})))

(deftest test-validate-optional-localized-field
  (is (= {:foo nil}
         (validate-optional-localized-field {:foo {:fi "FI" :en "EN"}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-optional-localized-field {:foo {:fi "FI"}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-optional-localized-field {:foo {:fi "FI" :en ""}} :foo [:fi :en])))
  (is (= {:foo {:en :t.form.validation/required}}
         (validate-optional-localized-field {:foo {:fi "FI" :en " "}} :foo [:fi :en]))))

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

(defn normalize-option-key
  "Strips disallowed characters from an option key"
  [key]
  (str/replace key #"\s+" ""))

(deftest test-normalize-option-key
  (is (= "foo" (normalize-option-key " f o o "))))

(defn- validate-key [option]
  (let [val (get option :key)]
    (cond
      (str/blank? val)
      {:key :t.form.validation/required}

      (not= val (normalize-option-key val))
      {:key :t.form.validation/invalid-value})))

(defn- validate-option [option id languages]
  {id (merge (validate-key option)
             (validate-localized-text-field option :label languages))})

(defn- validate-options [options languages]
  {:field/options
   (if (empty? options)
     :t.form.validation/options-required
     (apply merge (mapv #(validate-option %1 %2 languages) options (range))))})

(defn- validate-columns [columns languages]
  {:field/columns
   (if (empty? columns)
     :t.form.validation/columns-required
     ;; columns have the same syntax as options for now
     (apply merge (mapv #(validate-option %1 %2 languages) columns (range))))})

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
    {key :t.form.validation/unsupported}))

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
                        (if (supports-columns? field)
                          (validate-columns (:field/columns field) languages)
                          (validate-not-present field :field/columns))
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

(defn- validate-form-name-fields [form languages]
  (-> (if (:form/title form)
        (validate-text-field form :form/title) ; deprecated
        (merge (validate-text-field form :form/internal-name)
               (validate-localized-text-field form :form/external-title languages)))
      remove-empty-keys
      nil-if-empty))

(deftest test-validate-form-name-fields
  (is (= {:form/internal-name :t.form.validation/required
          :form/external-title {:en :t.form.validation/required
                                :fi :t.form.validation/required}}
         (validate-form-name-fields {} [:en :fi])))
  (is (= {:form/external-title {:fi :t.form.validation/required}}
         (validate-form-name-fields {:form/internal-name "foo"
                                     :form/external-title {:en "Foo"}} [:en :fi])
         (validate-form-name-fields {:form/internal-name "foo"
                                     :form/external-title {:en "Foo" :fi ""}} [:en :fi])))
  (is (= nil
         (validate-form-name-fields {:form/internal-name "foo"
                                     :form/external-title {:en "Foo" :fi "Foo"}} [:en :fi])))
  (is (= nil
         (validate-form-name-fields {:form/title "Foo"} [:en :fi]))
      "deprecated but still ok"))

(defn validate-form-template [form languages]
  (-> (merge (validate-organization-field form)
             (validate-form-name-fields form languages)
             {:form/fields (validate-fields (:form/fields form) languages)})
      remove-empty-keys
      nil-if-empty))

(deftest validate-form-template-test
  (let [form {:organization {:organization/id "abc"}
              :form/internal-name "the title"
              :form/external-title {:en "en title"
                                    :fi "fi title"}
              :form/fields [{:field/id "fld1"
                             :field/title {:en "en title"
                                           :fi "fi title"}
                             :field/info-text {:en "en info text"
                                               :fi "fi info text"}
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

    (testing "missing internal-name"
      (is (= :t.form.validation/required
             (:form/internal-name (validate-form-template (dissoc form :form/internal-name) languages))
             (:form/internal-name (validate-form-template (assoc-in form [:form/internal-name] "") languages)))))

    (testing "missing external-title"
      (is (= {:en :t.form.validation/required}
             (:form/external-title (validate-form-template (assoc-in form [:form/external-title :en] "") languages))
             (:form/external-title (validate-form-template (update-in form [:form/external-title] dissoc :en) languages)))))

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

    (testing "placeholder & max-length & info text shouldn't be present if they are not applicable"
      (let [form (-> form
                     (assoc-in [:form/fields 0 :field/type] :label)
                     (assoc-in [:form/fields 0 :field/placeholder :fi] "")
                     (assoc-in [:form/fields 0 :field/info-text :fi] ""))]
        (is (= {:form/fields {0 {:field/max-length :t.form.validation/unsupported
                                 :field/placeholder :t.form.validation/unsupported
                                 :field/info-text :t.form.validation/unsupported}}}
               (validate-form-template form languages)))))

    (testing "privacy, & options shouldn't be present if they are not applicable"
      (let [form (assoc form :form/fields
                        [{:field/title {:en "en" :fi "fi"}
                          :field/type :header
                          :field/privacy :invalid
                          :field/options [{:invalid-key :value}]}])]
        (is (= {:form/fields {0 {:field/privacy :t.form.validation/unsupported
                                 :field/options :t.form.validation/unsupported}}}
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

        (testing "missing options"
          (is (= {:form/fields {0 {:field/options :t.form.validation/options-required}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options] []) languages)
                 (validate-form-template (update-in form [:form/fields 0] dissoc :field/options) languages))))

        (testing "missing option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/required}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages))))

        (testing "invalid option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/invalid-value}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "e gg") languages))))

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

        (testing "missing options"
          (is (= {:form/fields {0 {:field/options :t.form.validation/options-required}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options] []) languages)
                 (validate-form-template (update-in form [:form/fields 0] dissoc :field/options) languages))))

        (testing "missing option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/required}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] nil) languages))))

        (testing "invalid option key"
          (is (= {:form/fields {0 {:field/options {0 {:key :t.form.validation/invalid-value}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :key] "e gg") languages))))

        (testing "missing option label"
          (let [empty-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] {:en "" :fi ""}) languages)
                nil-label (validate-form-template (assoc-in form [:form/fields 0 :field/options 0 :label] nil) languages)]
            (is (= {:form/fields {0 {:field/options {0 {:label {:en :t.form.validation/required
                                                                :fi :t.form.validation/required}}}}}}
                   empty-label
                   nil-label))))))

    (testing "phone number"
      (let [form (assoc form :form/fields
                        [{:field/type :phone-number
                          :field/title {:en "en" :fi "fi"}
                          :field/optional false}])]
        (testing "valid form"
          (is (empty? (validate-form-template form languages))))

        (testing "missing title localization"
          (is (= {:form/fields {0 {:field/title {:en :t.form.validation/required}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] nil) languages))))))

    (testing "ip address"
      (let [form (assoc form :form/fields
                        [{:field/type :ip-address
                          :field/title {:en "en" :fi "fi"}
                          :field/optional false}])]
        (testing "valid form"
          (is (empty? (validate-form-template form languages))))

        (testing "missing title localization"
          (is (= {:form/fields {0 {:field/title {:en :t.form.validation/required}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] nil) languages))))))

    (testing "table"
      (let [form (assoc form :form/fields
                        [{:field/type :table
                          :field/title {:en "en" :fi "fi"}
                          :field/columns [{:key "col1"
                                           :label {:en "en" :fi "fi"}}
                                          {:key "col2"
                                           :label {:en "en" :fi "fi"}}]
                          :field/optional false}])]
        (testing "valid form"
          (is (empty? (validate-form-template form languages))))

        (testing "missing columns"
          (is (= {:form/fields {0 {:field/columns :t.form.validation/columns-required}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/columns] []) languages)
                 (validate-form-template (update-in form [:form/fields 0] dissoc :field/columns) languages))))

        (testing "missing title localization"
          (is (= {:form/fields {0 {:field/title {:en :t.form.validation/required}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/title :en] nil) languages))))

        (testing "missing column localization"
          (is (= {:form/fields {0 {:field/columns {0 {:label {:en :t.form.validation/required}}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/columns 0 :label :en] "") languages)
                 (validate-form-template (assoc-in form [:form/fields 0 :field/columns 0 :label :en] nil) languages))))

        (testing "missing column key"
          (is (= {:form/fields {0 {:field/columns {0 {:key :t.form.validation/required}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/columns 0 :key] nil) languages)
                 (validate-form-template (update-in form [:form/fields 0 :field/columns 0] dissoc :key) languages))))

        (testing "invalid option key"
          (is (= {:form/fields {0 {:field/columns {0 {:key :t.form.validation/invalid-value}}}}}
                 (validate-form-template (assoc-in form [:form/fields 0 :field/columns 0 :key] "col 1") languages))))))

    (testing "visible"
      (let [form (assoc form :form/fields
                        [{:field/id "op"
                          :field/title {:en "en" :fi "fi"}
                          :field/type :option
                          :field/options [{:key "yes"
                                           :label {:en "en yes"
                                                   :fi "fi yes"}}
                                          {:key "no"
                                           :label {:en "en no"
                                                   :fi "fi no"}}]}
                         {:field/id "mul"
                          :field/title {:en "en" :fi "fi"}
                          :field/type :multiselect
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
                                    :visibility/field {:field/id "op"}})))
          (is (= {:form/fields {1 {:field/visibility {:visibility/values :t.form.validation/invalid-value}}}}
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "op"}
                                    :visibility/values ["does-not-exist"]})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "op"}
                                    :visibility/values ["yes" "does-not-exist"]})
                 (validate-visible {:visibility/type :only-if
                                    :visibility/field {:field/id "op"}
                                    :visibility/values ["yes" "yes"]}))))

        (testing "correct data"
          (is (empty? (validate-visible {:visibility/type :always})))
          (is (empty? (validate-visible {:visibility/type :only-if
                                         :visibility/field {:field/id "op"}
                                         :visibility/values ["yes"]})))
          (is (empty? (validate-visible {:visibility/type :only-if
                                         :visibility/field {:field/id "mul"}
                                         :visibility/values ["yes"]})))
          (is (empty? (validate-visible {:visibility/type :only-if
                                         :visibility/field {:field/id "mul"}
                                         :visibility/values ["yes" "no"]}))))))))
