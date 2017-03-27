(ns rems.test.form
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [ring.mock.request :refer :all]
            [rems.db.core :as db]
            rems.db.applications
            [rems.form :as form]
            [rems.test.tempura :refer [with-fake-tempura]]))

(def field #'form/field)

(deftest test-license-field
  (let [f (field {:type "license" :licensetype "link" :textcontent "ab.c" :title "Link to license"})
        [[_ attrs]] (hiccup-find [:input] f)
        [[_ target]] (hiccup-find [:a] f)]
    (is (= "checkbox" (:type attrs))
        "Checkbox exists for supported license type")
    (is (= "_blank" (:target target))
        "License with type link opens to a separate tab"))
  (let [f (field {:type "license" :licensetype "attachment" :textcontent "ab.c" :title "Link to license"})]
    (is (.contains (hiccup-text f) "Unsupported field ")
        "Unsupported license type gives a warning")))

(def validate #'form/validate)

(deftest test-validate
  (with-fake-tempura
    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional true
                             :value nil}
                            {:title "B"
                             :optional false
                             :value "xyz"}
                            {:title "C"
                             :optional false
                             :value "1"}]})))
    (let [res (validate
               {:items [{:title "A"
                         :optional true
                         :value nil}
                        {:title "B"
                         :optional false
                         :value ""}
                        {:title "C"
                         :optional false
                         :value nil}]})]
      (testing res
        (is (vector? res))
        (is (= 2 (count res)))
        (is (.contains (first res) "B"))
        (is (.contains (second res) "C"))))))

(deftest test-save
  (with-fake-tempura
    ;; simple mock db
    (let [world (atom {})
          run (fn [path params]
                (form/form-routes
                 (assoc (request :post path)
                        :form-params params)))]
      (with-redefs
        [rems.db.applications/get-form-for
         (fn [_ & [application]]
           {:id 137
            :application application
            :state (get-in @world [:states application])
            :items [{:id 61
                     :title "A"
                     :type "text"
                     :optional true
                     :value (get-in @world [:values application 61])}
                    {:id 62
                     :title "B"
                     :type "text"
                     :optional false
                     :value (get-in @world [:values application 62])}]})

         db/save-field-value!
         (fn [{application :application
               item :item
               value :value}]
           (swap! world assoc-in [:values application item] value))

         db/update-application-state!
         (fn [{id :id state :state}]
           (swap! world assoc-in [:states id] state))

         db/create-application!
         (constantly {:id 2})]

        (testing "first save"
          (let [resp (run "/form/7/save" {"field61" "x"
                                          "field62" "y"})
                flash (:flash resp)
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:states {2 "draft"} :values {2 {61 "x", 62 "y"}}}
                   @world))))

        (testing "second save, with missing optional field. shouldn't create new draft"
          (let [resp (run "/form/7/save" {"field61" ""
                                          "field62" "z"})
                flash (:flash resp)
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:states {2 "draft"} :values {2 {61 "", 62 "z"}}}
                   @world))))

        (testing "save with missing mandatory field"
          (let [resp (run "/form/7/2/save" {"field61" "w"
                                            "field62" ""})
                flash (:flash resp)
                flash-text (hiccup-text (:contents flash))]
            (testing flash
              (is (= :warning (:status flash)))
              (is (.contains flash-text "\"B\"")))
            (is (= {:states {2 "draft"} :values {2 {61 "w", 62 ""}}}
                   @world))))

        (testing "submit with missing mandatory field"
          (let [resp (run "/form/7/2/save" {"field61" "u"
                                            "field62" ""
                                            "submit" "true"})
                flash (:flash resp)
                flash-text (hiccup-text (:contents flash))]
            (testing flash
              (is (= :warning (:status flash)))
              (is (.contains flash-text "\"B\""))
              (is (.contains flash-text "saved"))
              (is (not (.contains flash-text "submitted"))))
            (is (= {:states {2 "draft"} :values {2 {61 "u", 62 ""}}}
                   @world))))

        (testing "successful submit"
          (let [resp (run "/form/7/2/save" {"field61" ""
                                            "field62" "v"
                                            "submit" "true"})
                flash (:flash resp)
                flash-text (hiccup-text (:contents flash))]
            (testing flash
              (is (= :success (:status flash)))
              (is (not (.contains flash-text "saved")))
              (is (.contains flash-text "submitted")))
            (is (= {:states {2 "applied"} :values {2 {61 "", 62 "v"}}}
                   @world))))))))
