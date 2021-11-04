(ns ^:browser rems.test-browser
  "REMS Browser tests.

  For the test database, you need to run these tests in the :test profile to get the right :database-url and :port.

  For development development tests, you can run against a running instance with:

  (rems.browser-test-util/init-driver! :chrome \"http://localhost:3000/\" :development)

  NB: Don't use etaoin directly but use it from the `browser-test-util` library that removes the need to pass the driver."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [com.rpl.specter :refer [select ALL]]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.form :as forms]
            [rems.api.services.invitation :as invitations]
            [rems.api.services.organizations :as organizations]
            [rems.api.services.resource :as resources]
            [rems.api.services.workflow :as workflows]
            [rems.browser-test-util :as btu]
            [rems.common.util :refer [getx]]
            [rems.config]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.users :as users]
            [rems.db.user-settings :as user-settings]
            [rems.standalone]
            [rems.testing-util :refer [with-user]]
            [rems.text :as text]))

(comment ; convenience for development testing
  (btu/init-driver! :chrome "http://localhost:3000/" :development))

(use-fixtures :each btu/fixture-refresh-driver)

(use-fixtures
  :once
  btu/ensure-empty-directories-fixture
  btu/test-dev-or-standalone-fixture
  btu/smoke-test
  btu/accessibility-report-fixture
  btu/fixture-init-driver)

;;; common functionality

(defn login-as [username]
  (btu/set-window-size 1400 7000) ; big enough to show the whole page in the screenshots
  (btu/go (btu/get-server-url))
  (btu/screenshot "landing-page.png")
  (btu/scroll-and-click {:css ".login-btn"})
  (btu/screenshot "login-page.png")
  (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text username}])
  ;; NB. it's better to use wait-visible instead of
  ;; eventually-visible? in utils like this because we want the line
  ;; in the original test in the error, not a line in this util with
  ;; no info of where it was called from.
  (btu/wait-visible :logout)
  (btu/screenshot "logged-in.png"))

(defn logout []
  (btu/scroll-and-click :logout)
  (btu/wait-visible {:css ".login-component"}))

(defn click-navigation-menu [link-text]
  (btu/scroll-and-click [:big-navbar {:tag :a :fn/text link-text}]))

(defn selected-navigation-menu? [link-text]
  (btu/has-class? [:big-navbar {:tag :a :fn/text link-text}] "active"))

(defn go-to-catalogue []
  (click-navigation-menu "Catalogue")
  (btu/wait-visible {:tag :h1 :fn/text "Catalogue"})
  (btu/wait-page-loaded)
  (btu/screenshot "catalogue-page.png"))

(defn go-to-applications []
  (click-navigation-menu "Applications")
  (btu/wait-visible {:tag :h1 :fn/text "Applications"})
  (btu/wait-page-loaded)
  (btu/screenshot "applications-page.png"))

(defn go-to-application [application-id]
  (btu/go (str (btu/get-server-url) "application/" application-id))
  (btu/wait-visible {:tag :h1 :fn/has-text "Application"})
  (btu/wait-page-loaded)
  (btu/screenshot "application-page.png"))

(defn click-administration-menu [link-text]
  (btu/scroll-and-click [:administration-menu {:tag :a :fn/text link-text}]))

(defn go-to-admin [link-text]
  (when-not (selected-navigation-menu? "Administration")
    (click-navigation-menu "Administration")
    (btu/wait-page-loaded))
  (click-administration-menu link-text)
  (btu/wait-visible {:tag :h1 :fn/text link-text})
  (btu/wait-page-loaded)
  (btu/screenshot (str "administration-page-" (str/replace link-text " " "-") ".png")))

(defn change-language [language]
  (btu/scroll-and-click [{:css ".language-switcher"} {:fn/text (.toUpperCase (name language))}]))

;;; catalogue page

(defn add-to-cart [resource-name]
  (btu/scroll-and-click [{:css "table.catalogue"}
                         {:fn/text resource-name}
                         {:xpath "./ancestor::tr"}
                         {:css ".add-to-cart"}]))

(defn click-cart-apply []
  (btu/scroll-and-click [{:css "table.cart"}
                         {:fn/has-text "Apply for"}
                         {:xpath "./ancestor::tr"}
                         {:css ".apply-for-catalogue-items"}])
  (btu/wait-visible {:tag :h1 :fn/has-text "Application"})
  (btu/wait-page-loaded)
  (btu/screenshot "application-page.png"))

;;; application page

(defn slurp-fields [selector]
  (->> (for [row (btu/query-all [selector {:fn/has-class :form-group}])
             :when (btu/visible-el? row)
             :let [k (btu/get-element-text-el (btu/child row {:tag :label}))
                   v (btu/first-value-of-el row [{:css ".form-control"}
                                                 {:css ".dropdown-container"}
                                                 {:css ".list-group"}])]]
         [k v])
       (into {})))

(defn slurp-rows [& selectors]
  (for [row (btu/query-all (vec (concat selectors [{:css "tr"}])))]
    (->> (for [td (btu/children row {:css "td"})
               :let [k (str/trim (btu/get-element-attr-el td "class"))
                     v (btu/first-value-of-el td)]]
           [k v])
         (into {}))))

(defn find-rows [table-selectors child-selector]
  (for [row (btu/query-all (vec (concat table-selectors [{:css "tr"}])))
        :when (seq (btu/children row child-selector))]
    row))

(defn click-row-action [table-selectors child-selector button-selector]
  (let [rows (seq (find-rows table-selectors child-selector))]
    (is (= 1 (count rows)))
    (btu/scroll-and-click-el
     (btu/child (first rows)
                button-selector))))

(defn- select-button-by-label [label]
  {:css ".btn" :fn/text label})

(comment
  (find-rows [:licenses]
             {:fn/text (str (btu/context-get :license-name) " EN")}))

(defn fill-form-field
  "Fills a form field named by `label` with `text`.

  Optionally give `:index` when several items match. It starts from 1.
  Optionally give `:form-nth` when several forms exist. It starts from 1."
  [label text & [opts]]
  (assert (> (:index opts 1) 0) "indexing starts at 1") ; xpath uses 1, let's keep the convention though we don't use xpath here because it will likely not work
  (assert (> (:form-index opts 1) 0) "indexing starts at 1") ; as above

  (let [index (dec (:index opts 1))
        form-index (dec (:form-index opts 1))
        id (-> (btu/query-all {:css ".fields"})
               (nth form-index)
               (btu/children {:tag :label :fn/has-text label})
               (nth index)
               (btu/get-element-attr-el :for))]
    ;; XXX: need to use `fill-human`, because `fill` is so quick that the form drops characters here and there
    (btu/fill-human {:id id} text)))

(defn set-date [id date]
  ;; XXX: The date format depends on operating system settings and is unaffected by browser locale,
  ;;      so we cannot reliably know the date format to type into the date field and anyways WebDriver
  ;;      support for date fields seems buggy. Changing the field with JavaScript is more reliable.
  (btu/js-execute
   ;; XXX: React workaround for dispatchEvent, see https://github.com/facebook/react/issues/10135
   "
   function setNativeValue(element, value) {
       const { set: valueSetter } = Object.getOwnPropertyDescriptor(element, 'value') || {}
       const prototype = Object.getPrototypeOf(element)
       const { set: prototypeValueSetter } = Object.getOwnPropertyDescriptor(prototype, 'value') || {}

       if (prototypeValueSetter && valueSetter !== prototypeValueSetter) {
           prototypeValueSetter.call(element, value)
       } else if (valueSetter) {
           valueSetter.call(element, value)
       } else {
           throw new Error('The given element does not have a value setter')
       }
   }
   var field = document.getElementById(arguments[0])
   setNativeValue(field, arguments[1])
   field.dispatchEvent(new Event('change', {bubbles: true}))
   "
   id date))

(defn set-date-for-label [label date]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/text label}]
                                 :for)]
    (set-date id date)))

(defn select-option [label option]
  (let [id (btu/get-element-attr [{:css ".fields"}
                                  {:tag :label :fn/has-text label}]
                                 :for)]
    (btu/fill {:id id} (str option "\n"))))

;; TODO  see if the select-option could be combined
(defn select-option*
  "Version of `select-option` that does not limit to .fields."
  [label option]
  (let [id (btu/get-element-attr [{:tag :label :fn/has-text label}]
                                 :for)]
    (btu/fill {:id id} (str option "\n"))))

(defn accept-licenses []
  (btu/scroll-and-click :accept-licenses-button)
  (btu/wait-visible :has-accepted-licenses))

(defn send-application []
  (btu/scroll-and-click :submit)
  (btu/wait-visible :status-success)
  (btu/wait-has-class :apply-phase "completed"))

(defn get-application-id []
  (last (str/split (btu/get-url) #"/")))

(defn get-attachments
  ([]
   (get-attachments {:css "a.attachment-link"}))
  ([selector]
   (mapv btu/get-element-text-el (btu/query-all selector))))

(defn get-validation-summary []
  (mapv btu/get-element-text-el (btu/query-all {:css "#flash-message-top ul li"})))

(defn get-error-summary []
  (mapv btu/get-element-text-el (btu/query-all {:css "#flash-message-actions > *"})))

(defn get-validation-for-field [label]
  (let [el (first (btu/query-all [{:css ".fields"}
                                  {:tag :label :fn/has-text label}]))
        id (btu/get-element-attr-el el :for)]
    (btu/get-element-text {:id (str id "-error")})))

;; TODO: return to DUO tests once features are complete
;; (defn get-duo-codes [s]
;;   (let [all-resources (-> (btu/query [{:class :application-resources}])
;;                           (btu/children {:fn/has-class :application-resource}))
;;         expected (->> all-resources
;;                       (filter #(seq (btu/children % {:class :resource-label :fn/has-text s})))
;;                       first)]
;;     (-> (btu/child expected {:class :resource-duo-codes})
;;         (btu/children {:tag :div :fn/has-class "pt-2"}))))

;; (defn get-duo-code [s s2]
;;   (let [has-code-value (re-pattern (str "(?s)^" s2 ".*"))]
;;     (->> (get-duo-codes s)
;;          (filter #(re-matches has-code-value (btu/value-of-el %)))
;;          first)))

;; (defn duo-code-fields [el]
;;   (->> (btu/children el {:fn/has-class :form-group})
;;        (map (fn [form]
;;               [(-> (btu/child form {:index 1})
;;                    btu/value-of-el)
;;                (-> (btu/child form {:index 2})
;;                    btu/value-of-el)]))))

;; applications page

(defn get-application-summary [application-id]
  (let [row (btu/query [{:css "table.my-applications"}
                        {:tag :tr :data-row application-id}])]
    {:id application-id
     :description (btu/get-element-text-el (btu/child row {:css ".description"}))
     :resource (btu/get-element-text-el (btu/child row {:css ".resource"}))
     :state (btu/get-element-text-el (btu/child row {:css ".state"}))}))

(defn- get-application-from-api [application-id & [userid]]
  (:body
   (http/get (str (btu/get-server-url) "/api/applications/" application-id)
             {:as :json
              :headers {"x-rems-api-key" "42"
                        "x-rems-user-id" (or userid "handler")}})))

;;; tests

(deftest test-new-application
  (btu/with-postmortem
    (login-as "alice")
    (btu/gather-axe-results)

    (testing "create application"
      (go-to-catalogue)
      (add-to-cart "Default workflow")
      (add-to-cart "Private form workflow")
      ;; (add-to-cart "Default workflow with DUO codes")
      (btu/gather-axe-results)
      (click-cart-apply)
      (btu/gather-axe-results)

      (btu/context-assoc! :application-id (get-application-id))

      (let [application (:body
                         (http/get (str (btu/get-server-url) "/api/applications/" (btu/context-get :application-id))
                                   {:as :json
                                    :headers {"x-rems-api-key" "42"
                                              "x-rems-user-id" "alice"}}))
            form-id (get-in application [:application/forms 0 :form/id])
            description-field-id (get-in application [:application/forms 0 :form/fields 1 :field/id])
            description-field-selector (keyword (str "form-" form-id "-field-" description-field-id))
            attachment-field (get-in application [:application/forms 0 :form/fields 7])
            attachment-field-id (str "form-" form-id "-field-" (:field/id attachment-field))
            attachment-field-upload-selector (keyword (str "upload-" attachment-field-id "-input"))
            conditional-field (get-in application [:application/forms 0 :form/fields 9])
            conditional-field-id (str "form-" form-id "-field-" (:field/id conditional-field))
            table-field (get-in application [:application/forms 0 :form/fields 11])
            table-field-id (str "form-" form-id "-field-" (:field/id table-field))]
        ;; sanity checks:
        (is (= "attachment" (:field/type attachment-field)))
        (is (= "table" (:field/type table-field)))
        (is (:field/visibility conditional-field))

        (fill-form-field "Application title field" "Test name")
        (fill-form-field "Text area" "Test2")
        (set-date-for-label "Date field" "2050-01-02")

        ;; TODO: return to DUO tests once features are complete
        ;; (testing "check resource DUO codes"
        ;;   (comment
        ;;     (-> (get-duo-code "Default workflow with DUO codes" "DS — disease specific research")
        ;;         (btu/child {:tag :button})
        ;;         btu/click-el))

        ;;   (let [res-label "Default workflow with DUO codes"]
        ;;     (is (btu/eventually-visible? [{:class :application-resources} {:class :resource-label :fn/has-text res-label}]))
        ;;     (is (= (->> (get-duo-codes res-label)
        ;;                 (map btu/value-of-el)
        ;;                 set)
        ;;            #{"DS — disease specific research"
        ;;              "RS — research specific restrictions"
        ;;              "COL — collaboration required"
        ;;              "GS — geographical restriction"
        ;;              "MOR — publication moratorium"
        ;;              "TS — time limit on use"
        ;;              "US — user specific restriction"
        ;;              "PS — project specific restriction"
        ;;              "IS — institution specific restriction"}))

        ;;     (testing "toggle DUO code open and verify fields"
        ;;       (let [duo-label "DS — disease specific research"
        ;;             duo (get-duo-code res-label duo-label)]
        ;;         (btu/click-el (btu/child duo {:tag :button}))
        ;;         (btu/eventually-visible? {:tag :div :fn/has-text "DUO:0000007"})
        ;;         (is (= (duo-code-fields duo)
        ;;                [["DUO code" "DUO:0000007"]
        ;;                 ["Description" "This data use permission indicates that use is allowed provided it is related to the specified disease."]

        ;;                 ["Disease (MONDO)" "MONDO:0000015"]]))))))

        (testing "upload three attachments, then remove one"
          (btu/upload-file attachment-field-upload-selector "test-data/test.txt")
          (btu/wait-predicate #(= ["test.txt"] (get-attachments)))
          (btu/upload-file attachment-field-upload-selector "test-data/test-fi.txt")
          (btu/wait-predicate #(= ["test.txt" "test-fi.txt"] (get-attachments)))
          (btu/upload-file attachment-field-upload-selector "test-data/test-sv.txt")
          (btu/wait-predicate #(= ["test.txt" "test-fi.txt" "test-sv.txt"] (get-attachments)))
          (btu/scroll-and-click-el (last (btu/query-all {:css (str "button.remove-attachment-" attachment-field-id)}))))

        (testing "uploading oversized attachment should display error"
          (let [reset-config (btu/set-client-config {:attachment-max-size 900})]
            (btu/upload-file attachment-field-upload-selector "resources/public/img/rems_logo_fi.png")
            (is (btu/eventually-visible? :status-failed))
            (is (= ["Upload an attachment: Failed"
                    (str/join "\n" ["The attachment is too large."
                                    "rems_logo_fi.png 10.34 KB"
                                    "Allowed maximum size of an attachment: 0.9 KB."])]
                   (get-error-summary)))
            (reset-config)))

        (is (not (btu/field-visible? "Conditional field"))
            "Conditional field is not visible before selecting option")

        (select-option "Option list" "First option")
        (btu/wait-predicate #(btu/field-visible? "Conditional field"))
        (fill-form-field "Conditional field" "Conditional")

        ;; check that answers to conditional fields are retained even if they're temporarily invisible
        (select-option "Option list" "Second option")
        (btu/wait-predicate #(not (btu/field-visible? "Conditional field")))
        (select-option "Option list" "First option")
        (btu/wait-predicate #(btu/field-visible? "Conditional field"))
        (is (= "Conditional" (btu/value-of (keyword conditional-field-id))))

        ;; pick two options for the multi-select field:
        (btu/check-box "Option2")
        (btu/check-box "Option3")
        ;; fill in two rows for the table
        (btu/scroll-and-click (keyword (str table-field-id "-add-row")))
        (is (btu/eventually-visible? (keyword (str table-field-id "-row0-col1"))))
        (btu/scroll-and-click (keyword (str table-field-id "-add-row")))
        (is (btu/eventually-visible? (keyword (str table-field-id "-row1-col1"))))
        (btu/fill-human (keyword (str table-field-id "-row0-col1")) "a")
        (btu/fill-human (keyword (str table-field-id "-row0-col2")) "b")
        (btu/fill-human (keyword (str table-field-id "-row1-col1")) "c")
        (btu/fill-human (keyword (str table-field-id "-row1-col2")) "d")

        ;; leave "Text field with max length" empty
        ;; leave "Text are with max length" empty

        (fill-form-field "Phone number" "+358450000100")
        (fill-form-field "IP address" "142.250.74.110")

        (fill-form-field "Simple text field" "Private field answer" {:form-index 2})

        (testing "save draft succesfully"
          (btu/scroll-and-click :save)
          (is (btu/eventually-visible? :status-success)))

        (testing "add invalid value for field, try to save"
          (fill-form-field "Email field" "user")
          (btu/scroll-and-click :save)
          (is (btu/eventually-visible? :status-failed))
          (is (= ["Invalid email address."]
                 (get-validation-summary)))
          (is (= "Invalid email address."
                 (get-validation-for-field "Email field"))))

        (fill-form-field "Email field" "@example.com")

        (testing "try to submit without accepting licenses or filling in a mandatory field"
          (btu/scroll-and-click :submit)
          (is (btu/eventually-visible? {:id "status-failed" :fn/has-text "Send application"}))
          (is (= ["Terms of use not accepted."
                  "Field \"Text field\" is required."]
                 (get-validation-summary)))
          (is (= "Field \"Text field\" is required."
                 (get-validation-for-field "Text field"))))

        (fill-form-field "Text field" "Test")

        (accept-licenses)
        (btu/gather-axe-results)

        (send-application)
        (btu/gather-axe-results)

        (is (= "Applied" (btu/get-element-text :application-state)))

        (testing "check a field answer"
          (is (= "Test name" (btu/get-element-text description-field-selector))))

        (testing "check that table field values are visible"
          (is (= "a" (btu/value-of (keyword (str table-field-id "-row0-col1")))))
          (is (= "b" (btu/value-of (keyword (str table-field-id "-row0-col2")))))
          (is (= "c" (btu/value-of (keyword (str table-field-id "-row1-col1")))))
          (is (= "d" (btu/value-of (keyword (str table-field-id "-row1-col2"))))))

        (testing "fetch application from API"
          (let [application (get-application-from-api (btu/context-get :application-id))]
            (btu/context-assoc! :attachment-ids (mapv :attachment/id (:application/attachments application)))

            (testing "see application on applications page"
              (go-to-applications)
              (btu/gather-axe-results)

              (is (= {:id (btu/context-get :application-id)
                      :resource "Default workflow, Private form workflow"
                      :state "Applied"
                      :description "Test name"}
                     (get-application-summary (btu/context-get :application-id)))))

            (testing "attachments"
              (is (= [{:attachment/id (first (btu/context-get :attachment-ids))
                       :attachment/filename "test.txt"
                       :attachment/type "text/plain"}
                      {:attachment/id (second (btu/context-get :attachment-ids))
                       :attachment/filename "test-fi.txt"
                       :attachment/type "text/plain"}]
                     (:application/attachments application))))
            (testing "applicant information"
              (is (= "alice" (get-in application [:application/applicant :userid])))
              (is (= (set (map :license/id (:application/licenses application)))
                     (set (get-in application [:application/accepted-licenses :alice])))))
            (testing "form fields"
              (is (= "Test name" (:application/description application)))
              (is (= [["label" ""]
                      ["description" "Test name"]
                      ["text" "Test"]
                      ["texta" "Test2"]
                      ["header" ""]
                      ["date" "2050-01-02"]
                      ["email" "user@example.com"]
                      ["attachment" (str/join "," (btu/context-get :attachment-ids))]
                      ["option" "Option1"]
                      ["text" "Conditional"]
                      ["multiselect" "Option2 Option3"]
                      ["table" [[{:column "col1", :value "a"} {:column "col2", :value "b"}]
                                [{:column "col1", :value "c"} {:column "col2", :value "d"}]]]
                      ["label" ""]
                      ["text" ""]
                      ["texta" ""]
                      ["phone-number" "+358450000100"]
                      ["ip-address" "142.250.74.110"]
                      ["text" "Private field answer"]]
                     (for [field (select [:application/forms ALL :form/fields ALL] application)]
                       ;; TODO could test other fields here too, e.g. title
                       [(:field/type field)
                        (:field/value field)]))))
            (testing "after navigating to the application view again"
              (btu/scroll-and-click [{:css "table.my-applications"}
                                     {:tag :tr :data-row (btu/context-get :application-id)}
                                     {:css ".btn-primary"}])
              (is (btu/eventually-visible? {:tag :h1 :fn/has-text "Application"}))
              (btu/wait-page-loaded)
              (btu/gather-axe-results)
              (testing "check a field answer"
                (is (= "Test name" (btu/get-element-text description-field-selector)))))))))))

(deftest test-applicant-member-invite-action
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-applicant-member-invite-action")))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-application (btu/context-get :application-id))

    (testing "invite member"
      (is (not (btu/visible? [:actions-invite-member {:fn/has-text "Invite member"}])))
      (btu/scroll-and-click :invite-member-action-button)
      (is (btu/eventually-visible? [:actions-invite-member {:fn/has-text "Invite member"}]))
      (btu/fill-human [:actions-invite-member :name-invite-member] "John Smith")
      (btu/fill-human [:actions-invite-member :email-invite-member] "john.smith@generic.name")
      (btu/scroll-and-click :invite-member)
      (btu/wait-invisible [:actions-invite-member {:fn/has-text "Invite member"}])
      (btu/scroll-and-click :invite0-info-collapse-more-link)
      (is (btu/eventually-visible? :invite0-info-collapse))

      (is (= {"Name" "John Smith"
              "Email" "john.smith@generic.name"}
             (slurp-fields :invite0-info)))
      (is (string? (-> (btu/context-get :application-id)
                       applications/get-application-internal
                       :application/invitation-tokens
                       keys
                       first)))
      (is (= {:event/actor "alice"
              :application/member {:name "John Smith"
                                   :email "john.smith@generic.name"}}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/invitation-tokens
                 vals
                 first)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant invited John Smith to the application."})))

    (testing "uninvite member"
      (is (not (btu/visible? :actions-invite0-operations-remove)))
      (btu/scroll-and-click :invite0-operations-remove-action-button)
      (is (btu/eventually-visible? :actions-invite0-operations-remove))
      (btu/fill-human :comment-invite0-operations-remove-comment "sorry but no")
      (btu/scroll-and-click :invite0-operations-remove-submit)
      (is (btu/eventually-visible? [{:css ".alert-success" :fn/has-text "Remove member: Success"}]))
      (btu/wait-invisible :actions-invite0-operations-remove)
      (btu/wait-invisible :invite0-info)

      (is (empty? (-> (btu/context-get :application-id)
                      applications/get-application-internal
                      :application/invitation-tokens)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant removed John Smith from the application."}))
      (is (btu/visible? {:css "div.event-comment" :fn/text "sorry but no"})))))

(deftest test-applicant-member-remove-action
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {:handlers ["handler"]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)
                                                                            :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-applicant-member-remove-action"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"})
    (test-helpers/create-user! {:eppn "ionna" :commonName "Ionna Insprucker" :mail "ionna@ins.mail"})
    (test-helpers/create-user! {:eppn "jade" :commonName "Jade Jenner" :mail "jade80@mail.name"})
    (test-helpers/create-user! {:eppn "kayla" :commonName "Kayla Kale" :mail "kale@is.good"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "ionna"}
                            :actor "handler"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "jade"}
                            :actor "handler"})
    (test-helpers/command! {:type :application.command/add-member
                            :application-id (btu/context-get :application-id)
                            :member {:userid "kayla"}
                            :actor "handler"}))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-application (btu/context-get :application-id))

    (testing "remove second member jade"
      (is (not (btu/visible? :actions-member1-operations-remove)))
      (btu/scroll-and-click :member1-operations-remove-action-button)
      (is (btu/eventually-visible? :actions-member1-operations-remove))
      (btu/fill-human :comment-member1-operations-remove-comment "not in research group anymore")
      (btu/scroll-and-click :member1-operations-remove-submit)
      (is (btu/eventually-visible? [{:css ".alert-success" :fn/has-text "Remove member: Success"}]))
      (btu/wait-invisible :actions-member1-operations-remove)
      (btu/wait-invisible :member2-info) ; last element is removed from DOM, remaining updated

      (is (= #{{:userid "ionna" :name "Ionna Insprucker" :email "ionna@ins.mail"}
               {:userid "kayla" :name "Kayla Kale" :email "kale@is.good"}}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/members)))
      (is (btu/visible? {:css "div.event-description" :fn/text "Alice Applicant removed Jade Jenner from the application."}))
      (is (btu/visible? {:css "div.event-comment" :fn/text "not in research group anymore"})))))

(deftest test-handling
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}
                                                                           {:field/title {:en "private en" :fi "private fi" :sv "private sv"}
                                                                            :field/optional false
                                                                            :field/type :text
                                                                            :field/privacy :private}]}))
    (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:title {:en "First catalogue item"
                                                                                    :fi "First catalogue item"
                                                                                    :sv "First catalogue item"}
                                                                            :form-id (btu/context-get :form-id)
                                                                            :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :catalogue-id2 (test-helpers/create-catalogue-item! {:title {:en "Second catalogue item (disabled)"
                                                                                     :fi "Second catalogue item (disabled)"
                                                                                     :sv "Second catalogue item (disabled)"}
                                                                             :form-id (btu/context-get :form-id)
                                                                             :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id) (btu/context-get :catalogue-id2)]
                                                                    "test-handling"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"})

    (testing "disabling the 2nd item"
      (binding [context/*user* {:eppn "owner"}
                context/*roles* #{:owner}]
        (catalogue/set-catalogue-item-enabled! {:id (btu/context-get :catalogue-id2) :enabled false}))))

  (btu/with-postmortem
    (login-as "developer")
    (testing "handler should see todos on logging in"
      (is (btu/eventually-visible? :todo-applications)))
    (testing "handler should see description of application"
      (is (btu/eventually-visible? {:class :application-description :fn/text "test-handling"})))
    (let [app-button {:tag :a :href (str "/application/" (btu/context-get :application-id))}]
      (testing "handler should see view button for application"
        (is (btu/eventually-visible? app-button)))
      (btu/scroll-and-click app-button))
    (testing "handler should see application after clicking on View"
      (is (btu/eventually-visible? {:tag :h1 :fn/has-text "test-handling"})))
    (testing "handler should see the applicant info"
      (btu/scroll-and-click :applicant-info-collapse-more-link)
      (is (= {"Name" "Alice Applicant"
              "Accepted terms of use" true
              "Username" "alice"
              "Email (from identity provider)" "alice@example.com"
              "Organization" "Default"
              "Nickname" "In Wonderland"
              "Applicant researcher status" true}
             (slurp-fields :applicant-info))))
    (testing "handler should see all form fields"
      (is (= {"description" "test-handling"
              "private en" "test-handling"}
             (slurp-fields {:css ".fields"}))))

    (testing "remove the disabled catalogue item"
      (is (btu/eventually-visible? {:css ".alert-warning"}) "sees disabled catalogue item warning")
      (btu/scroll-and-click :change-resources-action-button)
      (is (btu/eventually-visible? [:actions-change-resources {:tag :div :fn/has-text "Second catalogue item (disabled)"}]) "can see the disabled item")
      (btu/scroll-and-click [:actions-change-resources {:tag :div :fn/has-text "Second catalogue item (disabled)"} {:xpath ".."} {:css "svg"}])
      (is (btu/eventually-invisible? [:actions-change-resources {:tag :div :fn/has-text "Second catalogue item (disabled)"}]))
      (btu/scroll-and-click :change-resources)
      (is (btu/eventually-visible? {:css ".alert-success"}) "has removed disabled item"))

    (testing "open the approve form"
      (btu/scroll-and-click :approve-reject-action-button))
    (testing "add a comment and two attachments"
      (is (btu/eventually-visible? :comment-approve-reject))
      (btu/fill-human :comment-approve-reject "this is a comment")
      (btu/upload-file :upload-approve-reject-input "test-data/test.txt")
      (is (btu/eventually-visible? [{:css "a.attachment-link"}]))
      (btu/upload-file :upload-approve-reject-input "test-data/test-fi.txt")
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt"]
                              (get-attachments))))
    (testing "add and remove a third attachment"
      (btu/upload-file :upload-approve-reject-input "resources/public/img/rems_logo_en.png")
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt" "rems_logo_en.png"]
                              (get-attachments)))
      (let [buttons (btu/query-all {:css "button.remove-attachment-approve-reject"})]
        (btu/click-el (last buttons)))
      (btu/wait-predicate #(= ["test.txt" "test-fi.txt"]
                              (get-attachments))))
    (testing "approve"
      (btu/scroll-and-click :approve)
      (btu/wait-predicate #(= "Approved" (btu/get-element-text :application-state))))
    (testing "event visible in eventlog"
      (is (btu/visible? {:css "div.event-description b" :fn/text "Developer approved the application."})))
    (testing "attachments visible in eventlog"
      (is (= ["test.txt" "test-fi.txt"]
             (get-attachments {:css "div.event a.attachment-link"}))))

    (testing "event via api"
      ;; Note the absence of :entitlement/end, c.f. test-approve-with-end-date
      (is (= {:application/id (btu/context-get :application-id)
              :event/type "application.event/approved"
              :application/comment "this is a comment"
              :event/actor "developer"}
             (-> (get-application-from-api (btu/context-get :application-id) "developer")
                 :application/events
                 last
                 (dissoc :event/id :event/time :event/attachments :event/actor-attributes)))))))

(deftest test-invite-decider
  (testing "create test data"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-invite-decider"))
    (test-helpers/submit-application (btu/context-get :application-id) "alice")
    (test-helpers/create-user! {:eppn "new-decider" :commonName "New Decider"}))
  (btu/with-postmortem
    (testing "handler invites decider"
      (login-as "developer")
      (go-to-application (btu/context-get :application-id))
      (is (btu/eventually-visible? {:tag :h1 :fn/has-text "test-invite-decider"}))

      (btu/scroll-and-click :request-decision-dropdown)
      (is (btu/eventually-visible? :invite-decider-action-button))
      (btu/scroll-and-click :invite-decider-action-button)

      (is (btu/eventually-visible? :name-invite-decider))
      (btu/fill-human :name-invite-decider "anybody will do")
      (btu/fill-human :email-invite-decider "user@example.com")
      (btu/scroll-and-click :invite-decider)
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (logout))
    (testing "get invite token"
      (let [[token invitation] (-> (btu/context-get :application-id)
                                   applications/get-application-internal
                                   :application/invitation-tokens
                                   first)]
        (is (string? token))
        (is (= {:application/decider {:name "anybody will do" :email "user@example.com"}
                :event/actor "developer"}
               invitation))
        (btu/context-assoc! :token token)))
    (testing "accept invitation"
      (btu/go (str (btu/get-server-url) "application/accept-invitation/" (btu/context-get :token)))
      (is (btu/eventually-visible? {:css ".login-btn"}))
      (btu/scroll-and-click {:css ".login-btn"})
      (is (btu/eventually-visible? [{:css ".users"} {:tag :a :fn/text "new-decider"}]))
      (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text "new-decider"}])
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? {:tag :h1 :fn/has-text "test-invite-decider"})))
    (testing "check decider-joined event"
      (is (= {:event/type :application.event/decider-joined
              :event/actor "new-decider"}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/events
                 last
                 (select-keys [:event/actor :event/type])))))
    (testing "submit decision"
      (btu/scroll-and-click :decide-action-button)
      (is (btu/eventually-visible? :comment-decide))
      (btu/fill-human :comment-decide "ok")
      (btu/scroll-and-click :decide-approve)
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? {:css ".alert-success"})))
    (testing "check decision event"
      (is (= {:application/decision :approved
              :application/comment "ok"
              :event/actor "new-decider"
              :event/type :application.event/decided}
             (-> (btu/context-get :application-id)
                 applications/get-application-internal
                 :application/events
                 last
                 (select-keys [:application/decision :application/comment :event/actor :event/type])))))))

(deftest test-invite-handler
  (testing "create test data"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :workflow-title (str "test-invite-handler " (btu/get-seed)))
    (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {:title (btu/context-get :workflow-title) :handlers []}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)
                                                                            :workflow-id (btu/context-get :workflow-id)}))
    (test-helpers/create-user! {:eppn "invited-person-id" :commonName "Invited Person Name"})
    (with-user "owner"
      (btu/context-assoc! :invitation-id (getx (invitations/create-invitation! {:userid "owner"
                                                                                :name "Dorothy Vaughan"
                                                                                :email "dorothy.vaughan@nasa.gov"
                                                                                :workflow-id (btu/context-get :workflow-id)}) :invitation/id))))
  (btu/with-postmortem
    (testing "get invitation token"
      (let [invitation (-> (btu/context-get :invitation-id) invitations/get-invitation-full)
            token (:invitation/token invitation)]
        (is (string? token))
        (btu/context-assoc! :token token)))

    (testing "accept invitation"
      (btu/go (str (btu/get-server-url) "accept-invitation?token=" (btu/context-get :token)))
      (is (btu/eventually-visible? {:css ".login-btn"}))
      (btu/scroll-and-click {:css ".login-btn"})
      (is (btu/eventually-visible? [{:css ".users"} {:tag :a :fn/text "invited-person-id"}]))
      (btu/scroll-and-click [{:css ".users"} {:tag :a :fn/text "invited-person-id"}])
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? {:tag :div :fn/has-text "Successfully joined workflow handling."}))
      (is (btu/eventually-visible? [:workflow {:fn/has-text (btu/context-get :workflow-title)}]))
      (is (= {"Organization" "The Default Organization"
              "Title" (btu/context-get :workflow-title)
              "Type" "Master workflow"
              "Handlers" "Invited Person Name"
              "Active" true
              "Forms" ""}
             (slurp-fields :workflow))))))

(deftest test-invite-reviewer
  (testing "create test data"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :private-form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "private" :fi "fi" :sv "sv"}
                                                                                    :field/optional true
                                                                                    :field/type :text
                                                                                    :field/privacy :private}]}))
    (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)
                                                                            :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :catalogue-id-2 (test-helpers/create-catalogue-item! {:form-id (btu/context-get :private-form-id)
                                                                              :workflow-id (btu/context-get :workflow-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)
                                                                     (btu/context-get :catalogue-id-2)]
                                                                    "test-invite-reviewer"))
    (test-helpers/submit-application (btu/context-get :application-id) "alice"))
  (btu/with-postmortem
    (testing "handler invites reviewer"
      (login-as "developer")
      (go-to-application (btu/context-get :application-id))
      (is (btu/eventually-visible? {:tag :h1 :fn/has-text "test-invite-reviewer"}))

      (btu/scroll-and-click :request-review-dropdown)
      (is (btu/eventually-visible? :request-review-action-button))
      (btu/scroll-and-click :request-review-action-button)

      (is (btu/eventually-visible? :actions-request-review))
      (btu/scroll-and-click [:actions-request-review
                             {:css ".form-group > .dropdown-container"}])
      (is (btu/eventually-visible? {:css ".dropdown-select__menu"}))

      (btu/scroll-and-click [{:css ".dropdown-select__menu"}
                             {:tag :div :fn/has-text "Carl Reviewer"}])

      (btu/scroll-and-click :request-review-button)
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (logout))
    (testing "reviewer should see applicant non-private form answers"
      (login-as "carl")
      (go-to-application (btu/context-get :application-id))
      (is (= (count (btu/query-all {:css ".fields"}))
             1))
      (is (= {"description" "test-invite-reviewer"}
             (slurp-fields {:css ".fields"}))))))

(deftest test-approve-with-end-date
  (testing "submit test data with API"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-approve-with-end-date"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"}))
  (btu/with-postmortem
    (login-as "developer")
    (btu/go (str (btu/get-server-url) "application/" (btu/context-get :application-id)))
    (is (btu/eventually-visible? {:tag :h1 :fn/has-text "test-approve-with-end-date"}))
    (testing "approve"
      (btu/scroll-and-click :approve-reject-action-button)
      (is (btu/eventually-visible? :comment-approve-reject))
      (btu/fill-human :comment-approve-reject "this is a comment")
      (set-date :approve-end "2100-05-06")
      (btu/scroll-and-click :approve)
      (btu/wait-predicate #(= "Approved" (btu/get-element-text :application-state))))
    (testing "event visible in eventlog"
      (is (btu/visible? {:css "div.event-description b" :fn/text "Developer approved the application. Access rights end 2100-05-06"})))
    (testing "event via api"
      (is (= {:application/id (btu/context-get :application-id)
              :event/type "application.event/approved"
              :application/comment "this is a comment"
              :entitlement/end "2100-05-06T23:59:59.000Z"
              :event/actor "developer"}
             (-> (get-application-from-api (btu/context-get :application-id) "developer")
                 :application/events
                 last
                 (dissoc :event/id :event/time :event/attachments :event/actor-attributes)))))))

(deftest test-guide-page
  (btu/with-postmortem
    (btu/go (str (btu/get-server-url) "guide"))
    (is (btu/eventually-visible? {:tag :h1 :fn/text "Component Guide"}))
    ;; if there is a js exception, nothing renders, so let's check
    ;; that we have lots of examples in the dom:
    (is (< 60 (count (btu/query-all {:class :example}))))))

(deftest test-language-change
  (btu/with-postmortem
    (testing "default language is English"
      (btu/go (btu/get-server-url))
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Welcome to REMS"}))
      (login-as "alice")
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Catalogue"}))
      (btu/wait-page-loaded))

    (testing "changing language while logged out"
      (logout)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Welcome to REMS"}))
      (change-language :fi)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Tervetuloa REMSiin"})))

    (testing "changed language must persist after login"
      (login-as "alice")
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Aineistoluettelo"}))
      (btu/wait-page-loaded))

    (testing "wait for language change to show in the db"
      (btu/wait-predicate #(= :fi (:language (user-settings/get-user-settings "alice")))))

    (testing "changed language must have been saved for user"
      (logout)
      (change-language :en)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Welcome to REMS"}))
      (btu/delete-cookies)
      (login-as "alice")
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Aineistoluettelo"})))

    (testing "changing language while logged in"
      (change-language :en)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Catalogue"})))

    (user-settings/delete-user-settings! "alice") ; clear language settings
    (is true))) ; avoid no assertions warning

(defn create-license []
  (testing "create license"
    (btu/with-postmortem
      (go-to-admin "Licenses")
      (btu/scroll-and-click :create-license)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create license"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (btu/scroll-and-click :licensetype-link)
      (fill-form-field "License name" (str (btu/context-get :license-name) " EN") {:index 1})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 1})
      (fill-form-field "License name" (str (btu/context-get :license-name) " FI") {:index 2})
      (fill-form-field "License link" "https://www.csc.fi/etusivu" {:index 2})
      (fill-form-field "License name" (str (btu/context-get :license-name) " SV") {:index 3})
      (fill-form-field "License link" "https://www.csc.fi/home" {:index 3})
      (btu/screenshot "about-to-create-license.png")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "License"}))
      (btu/wait-page-loaded)
      (btu/screenshot "created-license.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title (EN)" (str (btu/context-get :license-name) " EN")
              "Title (FI)" (str (btu/context-get :license-name) " FI")
              "Title (SV)" (str (btu/context-get :license-name) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" true}
             (slurp-fields :license)))
      (go-to-admin "Licenses")
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Licenses"}))
      (is (some #{{"organization" "NBN"
                   "title" (str (btu/context-get :license-name) " EN")
                   "type" "link"
                   "active" true
                   "commands" "ViewDisableArchive"}}
                (slurp-rows :licenses)))
      (click-row-action [:licenses]
                        {:fn/text (str (btu/context-get :license-name) " EN")}
                        (select-button-by-label "View"))
      (is (btu/eventually-visible? :license))
      (is (= {"Organization" "NBN"
              "Title (EN)" (str (btu/context-get :license-name) " EN")
              "Title (FI)" (str (btu/context-get :license-name) " FI")
              "Title (SV)" (str (btu/context-get :license-name) " SV")
              "Type" "link"
              "External link (EN)" "https://www.csc.fi/home"
              "External link (FI)" "https://www.csc.fi/etusivu"
              "External link (SV)" "https://www.csc.fi/home"
              "Active" true}
             (slurp-fields :license))))))

;; TODO: return to DUO tests once features are complete
;; (def duo-codes (->> (slurp "duo.edn")
;;                     clojure.edn/read-string
;;                     (group-by :id)))

;; (defn select-duo-code [code]
;;   (let [duo (first (get duo-codes code))
;;         shorthand (:shorthand duo)
;;         label (get-in duo [:label :en])]
;;     (select-option "DUO codes" (str shorthand " — " label))))

(defn create-resource []
  (testing "create resource"
    (btu/with-postmortem
      (go-to-admin "Resources")
      (btu/scroll-and-click :create-resource)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create resource"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Resource identifier" (btu/context-get :resid))
      (select-option "License" (str (btu/context-get :license-name) " EN"))
      ;; (select-duo-code "DUO:0000026")
      ;; (fill-form-field "Approved user(s)" "developers developers developers")
      (btu/screenshot "about-to-create-resource.png")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Resource"}))
      (btu/wait-page-loaded)
      (btu/screenshot "created-resource.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Resource" (btu/context-get :resid)
              "Active" true}
             (slurp-fields :resource)))
      (is (= (str "License \"" (btu/context-get :license-name) " EN\"")
             (btu/get-element-text [:licenses {:class :license-title}])))
      (go-to-admin "Resources")
      (is (some #(= (btu/context-get :resid) (get % "title"))
                (slurp-rows :resources))))))


(defn create-form []
  (testing "create form"
    (btu/with-postmortem
      (go-to-admin "Forms")
      (btu/scroll-and-click :create-form)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create form"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Name" (btu/context-get :form-name))
      (fill-form-field "EN" (str (btu/context-get :form-name) " EN"))
      (fill-form-field "FI" (str (btu/context-get :form-name) " FI"))
      (fill-form-field "SV" (str (btu/context-get :form-name) " SV"))
      ;; TODO: create fields
      (btu/screenshot "about-to-create-form.png")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Form"}))
      (btu/wait-page-loaded)
      (btu/screenshot "created-form.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Name" (btu/context-get :form-name)
              "Title (EN)" (str (btu/context-get :form-name) " EN")
              "Title (FI)" (str (btu/context-get :form-name) " FI")
              "Title (SV)" (str (btu/context-get :form-name) " SV")
              "Active" true}
             (slurp-fields :form)))
      (go-to-admin "Forms")
      (is (some #(= (btu/context-get :form-name) (get % "internal-name"))
                (slurp-rows :forms))))))

(defn create-workflow []
  (testing "create workflow"
    (btu/with-postmortem
      (go-to-admin "Workflows")
      (btu/scroll-and-click :create-workflow)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create workflow"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :workflow-name))
      ;; Default workflow is already checked
      (select-option "Handlers" "handler")
      ;; No form
      (btu/screenshot "about-to-create-workflow.png")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Workflow"}))
      (btu/wait-page-loaded)
      (btu/screenshot "created-workflow.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :workflow-name)
              "Type" "Default workflow"
              "Handlers" "Hannah Handler (handler@example.com)"
              "Forms" ""
              "Active" true}
             (slurp-fields :workflow)))
      (go-to-admin "Workflows")
      (is (some #(= (btu/context-get :workflow-name) (get % "title"))
                (slurp-rows :workflows))))))

(defn create-catalogue-item []
  (testing "create catalogue item"
    (btu/with-postmortem
      (go-to-admin "Catalogue items")
      (btu/scroll-and-click :create-catalogue-item)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create catalogue item"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :catalogue-item-name) {:index 1})
      (fill-form-field "Title" (str (btu/context-get :catalogue-item-name) " FI") {:index 2})
      (fill-form-field "Title" (str (btu/context-get :catalogue-item-name) " SV") {:index 3})
      (select-option "Workflow" (btu/context-get :workflow-name))
      (select-option "Resource" (btu/context-get :resid))
      (when-let [form-name (btu/context-get :form-name)]
        (select-option "Form" form-name))
      (btu/screenshot "about-to-create-catalogue-item.png")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Catalogue item"}))
      (btu/wait-page-loaded)
      (btu/screenshot "created-catalogue-item.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title (EN)" (btu/context-get :catalogue-item-name)
              "Title (FI)" (str (btu/context-get :catalogue-item-name) " FI")
              "Title (SV)" (str (btu/context-get :catalogue-item-name) " SV")
              "More info (EN)" ""
              "More info (FI)" ""
              "More info (SV)" ""
              "Workflow" (btu/context-get :workflow-name)
              "Resource" (btu/context-get :resid)
              "Form" (or (btu/context-get :form-name)
                         "")
              "Active" false
              "End" ""}
             (dissoc (slurp-fields :catalogue-item)
                     "Start")))
      (go-to-admin "Catalogue items")
      (is (some #(= {"workflow" (btu/context-get :workflow-name)
                     "resource" (btu/context-get :resid)
                     "form" (or (btu/context-get :form-name)
                                "No form")
                     "name" (btu/context-get :catalogue-item-name)}
                    (select-keys % ["resource" "workflow" "form" "name"]))
                (slurp-rows :catalogue))))))

(defn enable-catalogue-item [item-name]
  (go-to-admin "Catalogue items")
  (btu/wait-page-loaded)
  ;; incidentally test search while we're at it
  (btu/fill-human :catalogue-search item-name)
  (btu/wait-page-loaded)
  (btu/screenshot "about-to-enable-catalogue-item.png")
  (btu/scroll-and-click {:tag :button :fn/text "Enable"})
  (btu/wait-page-loaded)
  (btu/screenshot "enabled-catalogue-item.png")
  (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))

(deftest test-create-catalogue-item
  (btu/with-postmortem
    (testing "create objects"
      (login-as "owner")
      (btu/context-assoc! :license-name (str "Browser Test License " (btu/get-seed))
                          :resid (str "browser.testing.resource/" (btu/get-seed))
                          :form-name (str "Browser Test Form " (btu/get-seed))
                          :workflow-name (str "Browser Test Workflow " (btu/get-seed))
                          :catalogue-item-name (str "Browser Test Catalogue Item " (btu/get-seed)))
      (create-license)
      (create-resource)
      (create-form)
      (create-workflow)
      (create-catalogue-item))
    (testing "check that catalogue item is not visible before enabling"
      (go-to-catalogue)
      (is (not (btu/visible? {:fn/text (btu/context-get :catalogue-item-name)}))))
    (testing "enable catalogue item"
      (enable-catalogue-item (btu/context-get :catalogue-item-name)))
    (testing "check that catalogue item is visible for applicants"
      (logout)
      (login-as "alice")
      (go-to-catalogue)
      (is (btu/visible? {:fn/text (btu/context-get :catalogue-item-name)})))
    (testing "catalogue item with no form"
      (logout)
      (login-as "owner")
      (btu/context-assoc! :form-name nil
                          :catalogue-item-name (str "Browser Test No Form " (btu/get-seed)))
      (create-catalogue-item))))

(deftest test-edit-catalogue-item
  (btu/with-postmortem
    (btu/context-assoc! :organization-id (str "organization " (btu/get-seed)))
    (btu/context-assoc! :organization-name (str "Organization " (btu/get-seed)))
    (btu/context-assoc! :organization (test-helpers/create-organization! {:organization/id (btu/context-get :organization-id)
                                                                          :organization/short-name {:en "ORGen" :fi "ORGfi" :sv "ORGsv"}
                                                                          :organization/name {:en (str (btu/context-get :organization-name) " en")
                                                                                              :fi (str (btu/context-get :organization-name) " fi")
                                                                                              :sv (str (btu/context-get :organization-name) " sv")}}))
    (btu/context-assoc! :workflow (test-helpers/create-workflow! {:title "test-edit-catalogue-item workflow"
                                                                  :type :workflow/default
                                                                  :organization {:organization/id (btu/context-get :organization-id)}
                                                                  :handlers ["handler"]}))
    (btu/context-assoc! :resource (test-helpers/create-resource! {:resource-ext-id "test-edit-catalogue-item resource"
                                                                  :organization {:organization/id (btu/context-get :organization-id)}}))
    (btu/context-assoc! :form (test-helpers/create-form! {:form/internal-name "test-edit-catalogue-item form"
                                                          :form/external-title {:en "Test Edit Catalogue Item Form EN"
                                                                                :fi "Test Edit Catalogue Item Form FI"
                                                                                :sv "Test Edit Catalogue Item Form SV"}
                                                          :form/fields []
                                                          :form/organization {:organization/id (btu/context-get :organization-id)}}))
    (btu/context-assoc! :catalogue-item (test-helpers/create-catalogue-item! {:title {:en "test-edit-catalogue-item EN"
                                                                                      :fi "test-edit-catalogue-item FI"
                                                                                      :sv "test-edit-catalogue-item SV"}
                                                                              :resource-id (btu/context-get :resource)
                                                                              :form-id (btu/context-get :form)
                                                                              :workflow-id (btu/context-get :workflow)
                                                                              :organization {:organization/id (btu/context-get :organization-id)}}))
    (login-as "owner")
    (btu/go (str (btu/get-server-url) "administration/catalogue-items/edit/" (btu/context-get :catalogue-item)))
    (btu/wait-page-loaded)
    (is (btu/eventually-visible? {:id :title-en :value "test-edit-catalogue-item EN"}))
    (btu/screenshot "test-edit-catalogue-item-1.png")
    (is (= {"Organization" (str (btu/context-get :organization-name) " en")
            "Title (EN)" "test-edit-catalogue-item EN"
            "Title (FI)" "test-edit-catalogue-item FI"
            "Title (SV)" "test-edit-catalogue-item SV"
            "More info URL (optional) (EN)" ""
            "More info URL (optional) (FI)" ""
            "More info URL (optional) (SV)" ""
            "Form" "test-edit-catalogue-item form"
            "Workflow" "test-edit-catalogue-item workflow"
            "Resource" "test-edit-catalogue-item resource"}
           (slurp-fields :catalogue-item-editor)))
    (btu/fill-human :infourl-en "http://google.com")
    (btu/screenshot "test-edit-catalogue-item-2.png")
    (btu/scroll-and-click :save)
    (is (btu/eventually-visible? {:tag :h1 :fn/text "Catalogue item"}))
    (btu/wait-page-loaded)
    (is (= {"Organization" (str (btu/context-get :organization-name) " en")
            "Title (EN)" "test-edit-catalogue-item EN"
            "Title (FI)" "test-edit-catalogue-item FI"
            "Title (SV)" "test-edit-catalogue-item SV"
            "More info (EN)" "http://google.com"
            "More info (FI)" ""
            "More info (SV)" ""
            "Form" "test-edit-catalogue-item form"
            "Workflow" "test-edit-catalogue-item workflow"
            "Resource" "test-edit-catalogue-item resource"
            "End" ""
            "Active" true}
           (dissoc (slurp-fields :catalogue-item) "Start")))
    (testing "after disabling the components"
      (with-user "owner"
        (organizations/set-organization-enabled! "owner" {:enabled false :organization/id (btu/context-get :organization-id)})
        (forms/set-form-enabled! {:id (btu/context-get :form) :enabled false})
        (resources/set-resource-enabled! {:id (btu/context-get :resource) :enabled false})
        (workflows/set-workflow-enabled! {:id (btu/context-get :workflow) :enabled false}))
      (testing "editing"
        (btu/go (str (btu/get-server-url) "administration/catalogue-items/edit/" (btu/context-get :catalogue-item)))
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:id :title-en :value "test-edit-catalogue-item EN"}))
        (is (= {"Organization" "Select..." ; unable to select a disabled org again
                "Title (EN)" "test-edit-catalogue-item EN"
                "Title (FI)" "test-edit-catalogue-item FI"
                "Title (SV)" "test-edit-catalogue-item SV"
                "More info URL (optional) (EN)" "http://google.com"
                "More info URL (optional) (FI)" ""
                "More info URL (optional) (SV)" ""
                "Form" "test-edit-catalogue-item form"
                "Workflow" "test-edit-catalogue-item workflow"
                "Resource" "test-edit-catalogue-item resource"}
               (dissoc (slurp-fields :create-catalogue-item) "Start"))))
      (testing "viewing"
        (btu/scroll-and-click :cancel)
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:id :title :fn/has-text "test-edit-catalogue-item EN"}))
        (is (= {"Organization" (str (btu/context-get :organization-name) " en")
                "Title (EN)" "test-edit-catalogue-item EN"
                "Title (FI)" "test-edit-catalogue-item FI"
                "Title (SV)" "test-edit-catalogue-item SV"
                "More info (EN)" "http://google.com"
                "More info (FI)" ""
                "More info (SV)" ""
                "Form" "test-edit-catalogue-item form"
                "Workflow" "test-edit-catalogue-item workflow"
                "Resource" "test-edit-catalogue-item resource"
                "End" ""
                "Active" true}
               (dissoc (slurp-fields :catalogue-item) "Start")))))))

(deftest test-form-editor
  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Forms")

    (testing "create form"
      (btu/scroll-and-click :create-form)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create form"}))
      (select-option "Organization" "nbn")
      (fill-form-field "Name" "Form editor test")
      (fill-form-field "EN" "Form Editor Test (EN)")
      (fill-form-field "FI" "Form Editor Test (FI)")
      (fill-form-field "SV" "Form Editor Test (SV)")
      (btu/scroll-and-click {:class :add-form-field})
      ;; using ids to fill the fields because the label structure is complicated
      (is (btu/eventually-visible? :fields-0-title-en))
      (btu/fill-human :fields-0-title-en "Text area (EN)")
      (btu/fill-human :fields-0-title-fi "Text area (FI)")
      (btu/fill-human :fields-0-title-sv "Text area (SV)")
      (btu/scroll-and-click :fields-0-placeholder-more-link)
      (is (btu/eventually-visible? :fields-0-placeholder-en))
      (btu/fill-human :fields-0-placeholder-en "Placeholder (EN)")
      (btu/fill-human :fields-0-placeholder-fi "Placeholder (FI)")
      (btu/fill-human :fields-0-placeholder-sv "Placeholder (SV)")
      (btu/scroll-and-click :fields-0-info-text-more-link)
      (is (btu/eventually-visible? :fields-0-info-text-en))
      (btu/fill-human :fields-0-info-text-en "") ; should not get passed as is blank
      (btu/fill-human :fields-0-info-text-fi " ")
      (btu/fill-human :fields-0-info-text-sv "")
      (btu/scroll-and-click :fields-0-type-texta)
      (btu/scroll-and-click :fields-0-optional)
      (btu/scroll-and-click :fields-0-additional-more-link)
      (btu/fill-human :fields-0-max-length "127")

      (testing "add and remove a field"
        (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
        (btu/scroll-and-click {:css "#field-editor-fld2 .form-field-controls .remove"})
        (btu/wait-has-alert)
        (btu/accept-alert)
        (btu/wait-invisible :field-editor-fld2))

      (testing "create all field types"

        (testing "create option field"
          ;; need to filter by visible-el? since there's a leftover invisible :add-form-field from the removed field
          (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
          (is (btu/eventually-visible? :fields-1-title-en))
          (btu/fill-human :fields-1-title-en "Option list (EN)")
          (btu/fill-human :fields-1-title-fi "Option list (FI)")
          (btu/fill-human :fields-1-title-sv "Option list (SV)")
          (btu/scroll-and-click :fields-1-info-text-more-link)
          (is (btu/eventually-visible? :fields-1-info-text-en))
          (btu/fill-human :fields-1-info-text-en "Info text (EN)")
          (btu/fill-human :fields-1-info-text-fi "Info text (FI)")
          (btu/fill-human :fields-1-info-text-sv "Info text (SV)")
          (btu/scroll-and-click :fields-1-additional-more-link)
          (btu/clear :fields-1-id)
          (btu/fill-human :fields-1-id "opt")
          (btu/scroll-and-click :fields-1-type-option)
          (btu/scroll-and-click {:class :add-option})
          (is (btu/eventually-visible? :fields-1-options-0-key))
          (btu/fill-human :fields-1-options-0-key "true")
          (btu/fill-human :fields-1-options-0-label-en "Yes")
          (btu/fill-human :fields-1-options-0-label-fi "Kyllä")
          (btu/fill-human :fields-1-options-0-label-sv "Ja")
          (btu/scroll-and-click {:class :add-option})
          (is (btu/eventually-visible? :fields-1-options-1-key))
          (btu/fill-human :fields-1-options-1-key "false")
          (btu/fill-human :fields-1-options-1-label-en "No")
          (btu/fill-human :fields-1-options-1-label-fi "Ei")
          (btu/fill-human :fields-1-options-1-label-sv "Nej"))

        (testing "create multi-select field"
          (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
          (btu/scroll-and-click :fields-2-type-multiselect)
          (is (btu/eventually-visible? :fields-2-add-option))
          (btu/fill-human :fields-2-title-en "Multi-select list (EN)")
          (btu/fill-human :fields-2-title-fi "Multi-select list (FI)")
          (btu/fill-human :fields-2-title-sv "Multi-select list (SV)")

          (btu/scroll-and-click :fields-2-add-option)
          (is (btu/eventually-visible? :fields-2-options-0-key))
          (btu/fill-human :fields-2-options-0-key "multi-select-option-0")
          (btu/fill-human :fields-2-options-0-label-en "Multi-select option 0 (EN)")
          (btu/fill-human :fields-2-options-0-label-fi "Multi-select option 0 (FI)")
          (btu/fill-human :fields-2-options-0-label-sv "Multi-select option 0 (SV)")

          (btu/scroll-and-click :fields-2-add-option)
          (is (btu/eventually-visible? :fields-2-options-1-key))
          (btu/fill-human :fields-2-options-1-key "multi-select-option-1")
          (btu/fill-human :fields-2-options-1-label-en "Multi-select option 1 (EN)")
          (btu/fill-human :fields-2-options-1-label-fi "Multi-select option 1 (FI)")
          (btu/fill-human :fields-2-options-1-label-sv "Multi-select option 1 (SV)")

          (is (= (->> (btu/query-all [{:id "container-form-1-field-fld2"} {:class "form-check"}])
                      (mapv btu/value-of-el))
                 ["multi-select-option-0"
                  "multi-select-option-1"]))
          (btu/scroll-and-click {:class "move-up fields-2-option-1"})
          (is (= (->> (btu/query-all [{:id "container-form-1-field-fld2"} {:class "form-check"}])
                      (mapv btu/value-of-el))
                 ["multi-select-option-1"
                  "multi-select-option-0"])))

        (testing "create table field"
          (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
          (btu/scroll-and-click :fields-3-type-table)
          (is (btu/eventually-visible? :fields-3-add-column))
          (btu/fill-human :fields-3-title-en "Table (EN)")
          (btu/fill-human :fields-3-title-fi "Table (FI)")
          (btu/fill-human :fields-3-title-sv "Table (SV)")

          (btu/scroll-and-click :fields-3-add-column)
          (btu/fill-human :fields-3-columns-0-key "table-column-0")
          (btu/fill-human :fields-3-columns-0-label-en "Table column 0 (EN)")
          (btu/fill-human :fields-3-columns-0-label-fi "Table column 0 (FI)")
          (btu/fill-human :fields-3-columns-0-label-sv "Table column 0 (SV)")

          (btu/scroll-and-click :fields-3-add-column)
          (btu/fill-human :fields-3-columns-1-key "table-column-1")
          (btu/fill-human :fields-3-columns-1-label-en "Table column 1 (EN)")
          (btu/fill-human :fields-3-columns-1-label-fi "Table column 1 (FI)")
          (btu/fill-human :fields-3-columns-1-label-sv "Table column 1 (SV)")

          (btu/scroll-and-click :fields-3-add-column)
          (btu/fill-human :fields-3-columns-2-key "table-column-2")
          (btu/fill-human :fields-3-columns-2-label-en "Table column 2 (EN)")
          (btu/fill-human :fields-3-columns-2-label-fi "Table column 2 (FI)")
          (btu/fill-human :fields-3-columns-2-label-sv "Table column 2 (SV)")

          (btu/fill-human :form-1-field-fld3-row0-table-column-0 "value 0")
          (btu/fill-human :form-1-field-fld3-row0-table-column-1 "value 1")
          (btu/fill-human :form-1-field-fld3-row0-table-column-2 "value 2")

          (is (= (->> (btu/query-all [{:id "container-form-1-field-fld3"} {:css "table > thead > tr > th"}])
                      (mapv btu/value-of-el)
                      (filter (comp not clojure.string/blank?)))
                 ["Table column 0 (EN)" "Table column 1 (EN)" "Table column 2 (EN)"]))
          (is (= (->> (btu/query-all [{:id "container-form-1-field-fld3"} {:css "table > tbody > tr > td > input.form-control"}])
                      (map btu/value-of-el))
                 ["value 0" "value 1" "value 2"])))

        (testing "create date field"
          (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
          (btu/scroll-and-click :fields-4-type-date)
          (is (btu/eventually-visible? {:id :form-1-field-fld4 :tag :input :css "[type=date]"}))

          (btu/fill-human :fields-4-title-en "Date field (EN)")
          (btu/fill-human :fields-4-title-fi "Date field (FI)")
          (btu/fill-human :fields-4-title-sv "Date field (SV)")

          (btu/scroll-and-click :fields-4-info-text-more-link)
          (is (btu/eventually-visible? :fields-4-info-text-en))
          (btu/fill-human :fields-4-info-text-en "Info text (EN)")
          (btu/fill-human :fields-4-info-text-fi "Info text (FI)")
          (btu/fill-human :fields-4-info-text-sv "Info text (SV)")
          (is (btu/eventually-visible? [:container-form-1-field-fld4 {:tag :button :fn/has-class "info-button"}]))

          (btu/scroll-and-click [:container-form-1-field-fld4 {:tag :button :fn/has-class "info-button"}])
          (is (btu/eventually-visible? :form-1-field-fld4-collapse))
          (is (= (btu/value-of :form-1-field-fld4-collapse)
                 "Info text (EN)"))))

      ;; TODO test validations?
      (btu/scroll-and-click :save))

    (testing "view form"
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Form"}))
      (btu/wait-page-loaded)
      (is (= {"Organization" "NBN"
              "Name" "Form editor test"
              "Title (EN)" "Form Editor Test (EN)"
              "Title (FI)" "Form Editor Test (FI)"
              "Title (SV)" "Form Editor Test (SV)"
              "Active" true}
             (slurp-fields :form)))
      (testing "preview"
        ;; the text is split into multiple DOM nodes so we need btu/has-text?, :fn/has-text is simpler for some reason
        (is (btu/eventually-visible? {:tag :button :fn/has-class :info-button}))
        (is (btu/has-text? {:tag :label :class :application-field-label :fn/has-text "Text area (EN)"}
                           "(max 127 characters)"))
        (is (btu/has-text? {:tag :label :class :application-field-label :fn/has-text "Text area (EN)"}
                           "(optional)"))
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"}))
        (is (btu/visible? {:tag :legend :class :application-field-label :fn/has-text "Multi-select list (EN)"}))
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Table (EN)"})))

      (testing "info collapse can be toggled"
        (is (not (btu/visible? {:tag :div :fn/has-class :info-collapse})))
        (is (not (btu/visible? {:tag :div :fn/has-text "Info text (EN)"})))
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (is (btu/eventually-visible? {:tag :div :fn/has-class :info-collapse}))
        (is (btu/visible? {:tag :div :fn/has-text "Info text (EN)"}))
        ;; TODO: figure out what to wait for
        (Thread/sleep 500)
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (btu/wait-invisible {:tag :div :fn/has-text "Info text (EN)"})
        (btu/wait-predicate #(not (btu/visible? {:tag :div :fn/has-text "Info text (EN)"})) {:timeout 30})
        (change-language :fi)
        (is (btu/eventually-visible? {:tag :label :class :application-field-label :fn/has-text "Text area (FI)"}))
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Text area (FI)"}))
        (btu/click-el (first (btu/query-all {:tag :button :fn/has-class :info-button})))
        (is (btu/eventually-visible? {:tag :div :fn/has-class :info-collapse}))
        (is (btu/visible? {:tag :div :fn/has-text "Info text (FI)"}))))

    (testing "edit form"
      (change-language :en)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Form"}))
      (btu/scroll-and-click {:fn/has-class :edit-form})
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Edit form"}))

      (testing "add description field"
        (btu/scroll-and-click {:class :add-form-field})
        (btu/scroll-and-click :fields-0-type-description)
        (btu/fill-human :fields-0-title-en "Description (EN)")
        (btu/fill-human :fields-0-title-fi "Description (FI)")
        (btu/fill-human :fields-0-title-sv "Description (SV)")

        (btu/scroll-and-click :save)
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:tag :h1 :fn/has-text "Form"}))
        (is (btu/visible? {:tag :label :class :application-field-label :fn/has-text "Option list (EN)"})))

      (testing "check that error message is present on field empty"
        (btu/scroll-and-click {:fn/has-class :edit-form})
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:tag :h1 :fn/text "Edit form"}))

        (btu/scroll-and-click :field-editor-fld5-collapse-more-link)
        (btu/scroll-and-click :fields-0-type-description)
        (btu/scroll-and-click :fields-0-info-text-more-link)
        (is (btu/eventually-visible? :fields-0-info-text-en))
        (btu/fill-human :fields-0-info-text-en "Info text (EN)")
        (btu/fill-human :fields-0-info-text-fi "Info text (FI)")
        (btu/fill-human :fields-0-info-text-sv " ")

        (btu/scroll-and-click :save)

        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:tag :h1 :fn/has-text "Edit form"}))
        (is (btu/visible? {:id :fields-0-info-text-sv :fn/has-class :is-invalid}))
        ;; :fn/has-text has trouble working for the whole "Field \"Field description (optional)\" is required." string
        (is (btu/visible? {:fn/has-class :invalid-feedback :fn/has-text "Field description (optional)"}))
        (is (btu/visible? {:fn/has-class :invalid-feedback :fn/has-text "is required"}))
        (is (btu/visible? {:fn/has-class :alert-danger :fn/has-text "Check the following errors"})))

      (testing "successful save"
        (btu/fill-human :fields-0-info-text-sv "Info text (SV)")
        (btu/scroll-and-click :save)
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? {:tag :h1 :fn/has-text "Form"}))))

    (testing "fetch form via api"
      (let [form-id (Integer/parseInt (last (str/split (btu/get-url) #"/")))]
        (is (= {:form/id form-id
                :organization {:organization/id "nbn" :organization/name {:fi "NBN" :en "NBN" :sv "NBN"} :organization/short-name {:fi "NBN" :en "NBN" :sv "NBN"}}
                :form/internal-name "Form editor test"
                :form/external-title {:en "Form Editor Test (EN)"
                                      :fi "Form Editor Test (FI)"
                                      :sv "Form Editor Test (SV)"}
                :form/title "Form editor test" ; deprecated
                :form/fields [{:field/title {:fi "Description (FI)" :en "Description (EN)" :sv "Description (SV)"}
                               :field/info-text {:en "Info text (EN)", :fi "Info text (FI)", :sv "Info text (SV)"}
                               :field/type "description"
                               :field/id "fld5"
                               :field/max-length nil
                               :field/optional false}
                              {:field/placeholder {:fi "Placeholder (FI)" :en "Placeholder (EN)" :sv "Placeholder (SV)"}
                               :field/title {:fi "Text area (FI)" :en "Text area (EN)" :sv "Text area (SV)"}
                               :field/type "texta"
                               :field/id "fld1"
                               :field/max-length 127
                               :field/optional true}
                              {:field/title {:fi "Option list (FI)" :en "Option list (EN)" :sv "Option list (SV)"}
                               :field/info-text {:en "Info text (EN)", :fi "Info text (FI)", :sv "Info text (SV)"}
                               :field/type "option"
                               :field/id "opt"
                               :field/options [{:key "true" :label {:fi "Kyllä" :en "Yes" :sv "Ja"}}
                                               {:key "false" :label {:fi "Ei" :en "No" :sv "Nej"}}]
                               :field/optional false}
                              {:field/id "fld2"
                               :field/optional false
                               :field/options [{:key "multi-select-option-1"
                                                :label {:en "Multi-select option 1 (EN)"
                                                        :fi "Multi-select option 1 (FI)"
                                                        :sv "Multi-select option 1 (SV)"}}
                                               {:key "multi-select-option-0"
                                                :label {:en "Multi-select option 0 (EN)"
                                                        :fi "Multi-select option 0 (FI)"
                                                        :sv "Multi-select option 0 (SV)"}}]
                               :field/title {:en "Multi-select list (EN)"
                                             :fi "Multi-select list (FI)"
                                             :sv "Multi-select list (SV)"}
                               :field/type "multiselect"}
                              {:field/columns [{:key "table-column-0"
                                                :label {:en "Table column 0 (EN)"
                                                        :fi "Table column 0 (FI)"
                                                        :sv "Table column 0 (SV)"}}
                                               {:key "table-column-1"
                                                :label {:en "Table column 1 (EN)"
                                                        :fi "Table column 1 (FI)"
                                                        :sv "Table column 1 (SV)"}}
                                               {:key "table-column-2"
                                                :label {:en "Table column 2 (EN)"
                                                        :fi "Table column 2 (FI)"
                                                        :sv "Table column 2 (SV)"}}]
                               :field/id "fld3"
                               :field/optional false
                               :field/title {:en "Table (EN)" :fi "Table (FI)" :sv "Table (SV)"}
                               :field/type "table"}
                              {:field/title {:fi "Date field (FI)" :en "Date field (EN)" :sv "Date field (SV)"}
                               :field/info-text {:en "Info text (EN)", :fi "Info text (FI)", :sv "Info text (SV)"}
                               :field/type "date"
                               :field/id "fld5"
                               :field/optional false}]
                :form/errors nil
                :enabled true
                :archived false}
               (:body
                (http/get (str (btu/get-server-url) "/api/forms/" form-id)
                          {:as :json
                           :headers {"x-rems-api-key" "42"
                                     "x-rems-user-id" "handler"}}))))))
    (user-settings/delete-user-settings! "owner"))) ; clear language settings

(deftest test-conditional-field
  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Forms")

    (testing "create form"
      (btu/scroll-and-click :create-form)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create form"}))
      (select-option "Organization" "nbn")
      (fill-form-field "Name" "Conditional field test")
      (fill-form-field "EN" "Conditional field test (EN)")
      (fill-form-field "FI" "Conditional field test (FI)")
      (fill-form-field "SV" "Conditional field test (SV)")

      (testing "create option field"
        (btu/scroll-and-click {:class :add-form-field})
        (is (btu/eventually-visible? :fields-0-title-en))
        (btu/fill-human :fields-0-title-en "Option (EN)")
        (btu/fill-human :fields-0-title-fi "Option (FI)")
        (btu/fill-human :fields-0-title-sv "Option (SV)")
        (btu/scroll-and-click :fields-0-type-option)
        (btu/scroll-and-click {:class :add-option})
        (is (btu/eventually-visible? :fields-0-options-0-key))
        (btu/fill-human :fields-0-options-0-key "true")
        (btu/fill-human :fields-0-options-0-label-en "Yes")
        (btu/fill-human :fields-0-options-0-label-fi "Kyllä")
        (btu/fill-human :fields-0-options-0-label-sv "Ja")
        (btu/scroll-and-click {:class :add-option})
        (is (btu/eventually-visible? :fields-0-options-1-key))
        (btu/fill-human :fields-0-options-1-key "false")
        (btu/fill-human :fields-0-options-1-label-en "No")
        (btu/fill-human :fields-0-options-1-label-fi "Ei")
        (btu/fill-human :fields-0-options-1-label-sv "Nej"))

      (testing "create multiselect field"
        (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
        (is (btu/eventually-visible? :fields-1-title-en))
        (btu/fill-human :fields-1-title-en "Multiselect (EN)")
        (btu/fill-human :fields-1-title-fi "Multiselect (FI)")
        (btu/fill-human :fields-1-title-sv "Multiselect (SV)")
        (btu/scroll-and-click :fields-1-type-multiselect)
        (btu/scroll-and-click :fields-1-add-option)
        (is (btu/eventually-visible? :fields-1-options-0-key))
        (btu/fill-human :fields-1-options-0-key "x")
        (btu/fill-human :fields-1-options-0-label-en "X")
        (btu/fill-human :fields-1-options-0-label-fi "X")
        (btu/fill-human :fields-1-options-0-label-sv "X")
        (btu/scroll-and-click :fields-1-add-option)
        (is (btu/eventually-visible? :fields-1-options-1-key))
        (btu/fill-human :fields-1-options-1-key "y")
        (btu/fill-human :fields-1-options-1-label-en "Y")
        (btu/fill-human :fields-1-options-1-label-fi "Y")
        (btu/fill-human :fields-1-options-1-label-sv "Y")
        (btu/scroll-and-click :fields-1-add-option)
        (is (btu/eventually-visible? :fields-1-options-2-key))
        (btu/fill-human :fields-1-options-2-key "z")
        (btu/fill-human :fields-1-options-2-label-en "Z")
        (btu/fill-human :fields-1-options-2-label-fi "Z")
        (btu/fill-human :fields-1-options-2-label-sv "Z"))

      (testing "create conditional text field"
        (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
        (is (btu/eventually-visible? :fields-2-title-en))
        (btu/fill-human :fields-2-title-en "Text (EN)")
        (btu/fill-human :fields-2-title-fi "Text (FI)")
        (btu/fill-human :fields-2-title-sv "Text (SV)")
        (btu/scroll-and-click :fields-2-additional-more-link)
        (is (btu/eventually-visible? :fields-2-visibility-type))
        (btu/fill :fields-2-visibility-type "Only if\n")
        (is (btu/eventually-visible? :fields-2-visibility-type))
        (btu/fill :fields-2-visibility-field "Field 1: Option (EN)\n")
        (is (btu/eventually-visible? :fields-2-visibility-type))
        (btu/fill :fields-2-visibility-value "Yes\n"))

      (testing "create conditional email field"
        (btu/scroll-and-click-el (last (btu/query-all {:class :add-form-field})))
        (is (btu/eventually-visible? :fields-3-title-en))
        (btu/fill-human :fields-3-title-en "Email (EN)")
        (btu/fill-human :fields-3-title-fi "Email (FI)")
        (btu/fill-human :fields-3-title-sv "Email (SV)")
        (btu/scroll-and-click :fields-3-type-email)
        (btu/scroll-and-click :fields-3-additional-more-link)
        (is (btu/eventually-visible? :fields-3-visibility-type))
        (btu/fill :fields-3-visibility-type "Only if\n")
        (is (btu/eventually-visible? :fields-3-visibility-field))
        (btu/fill :fields-3-visibility-field "Field 2: Multiselect (EN)\n")
        (is (btu/eventually-visible? :fields-3-visibility-value))
        (btu/fill :fields-3-visibility-value "X\n")
        (btu/fill :fields-3-visibility-value "Z\n"))

      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Form"}))
      (btu/context-assoc! :form-id (Integer/parseInt (last (str/split (btu/get-url) #"/")))))

    (testing "fetch form via api"
      (is (= {:form/title "Conditional field test"
              :form/external-title {:fi "Conditional field test (FI)" :en "Conditional field test (EN)" :sv "Conditional field test (SV)"}
              :organization {:organization/id "nbn"
                             :organization/short-name {:fi "NBN" :en "NBN" :sv "NBN"}
                             :organization/name {:fi "NBN" :en "NBN" :sv "NBN"}}
              :form/errors nil
              :form/id (btu/context-get :form-id)

              :form/internal-name "Conditional field test"
              :form/fields [{:field/title {:fi "Option (FI)" :en "Option (EN)" :sv "Option (SV)"}
                             :field/type "option"
                             :field/id "fld1"
                             :field/optional false
                             :field/options [{:key "true" :label {:fi "Kyllä" :en "Yes" :sv "Ja"}}
                                             {:key "false" :label {:fi "Ei" :en "No" :sv "Nej"}}]}
                            {:field/title {:fi "Multiselect (FI)" :en "Multiselect (EN)" :sv "Multiselect (SV)"}
                             :field/type "multiselect"
                             :field/id "fld2"
                             :field/optional false
                             :field/options [{:key "x" :label {:fi "X" :en "X" :sv "X"}}
                                             {:key "y" :label {:fi "Y" :en "Y" :sv "Y"}}
                                             {:key "z" :label {:fi "Z" :en "Z" :sv "Z"}}]}
                            {:field/title {:fi "Text (FI)" :en "Text (EN)" :sv "Text (SV)"}
                             :field/type "text"
                             :field/visibility {:visibility/type "only-if"
                                                :visibility/field {:field/id "fld1"}
                                                :visibility/values ["true"]}
                             :field/id "fld3"
                             :field/max-length nil
                             :field/optional false}
                            {:field/title {:fi "Email (FI)" :en "Email (EN)" :sv "Email (SV)"}
                             :field/type "email"
                             :field/visibility {:visibility/type "only-if"
                                                :visibility/field {:field/id "fld2"}
                                                :visibility/values ["x" "z"]}
                             :field/id "fld4"
                             :field/optional false}]
              :archived false
              :enabled true}
             (:body
              (http/get (str (btu/get-server-url) "/api/forms/" (btu/context-get :form-id))
                        {:as :json
                         :headers {"x-rems-api-key" "42"
                                   "x-rems-user-id" "handler"}})))))

    (testing "create catalogue item and application"
      (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
      (btu/context-assoc! :application-id (test-helpers/create-application! {:actor "alice"
                                                                             :catalogue-item-ids [(btu/context-get :catalogue-id)]})))

    (testing "fill in application"
      (logout)
      (login-as "alice")
      (go-to-application (btu/context-get :application-id))
      (is (btu/eventually-visible? {:tag :h1 :fn/has-text "Application"}))
      (is (btu/field-visible? "Option (EN)"))
      (is (btu/field-visible? "Multiselect (EN)"))

      (testing "toggle text field visibility"
        (is (not (btu/field-visible? "Text (EN)")))
        (select-option "Option (EN)" "Yes")
        (is (btu/field-visible? "Text (EN)"))
        (select-option "Option (EN)" "No")
        (is (not (btu/field-visible? "Text (EN)"))))

      (testing "toggle email field visibility"
        (is (not (btu/field-visible? "Email (EN)")))
        (btu/check-box "x") ; x selected
        (is (btu/no-timeout? (fn [] (btu/wait-predicate #(btu/field-visible? "Email (EN)")))))
        (btu/check-box "z") ; x and z selected
        (is (btu/field-visible? "Email (EN)"))
        (btu/check-box "x") ; z selected
        (Thread/sleep 500) ; Small wait to make sure the field really stays visible
        (is (btu/field-visible? "Email (EN)"))
        (btu/check-box "y") ; z and y selected
        (is (btu/field-visible? "Email (EN)"))
        (btu/check-box "z") ; y zelected
        (is (btu/no-timeout? (fn [] (btu/wait-predicate #(not (btu/field-visible? "Email (EN)"))))))))))

(deftest test-workflow-create-edit
  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Workflows")
    (testing "create workflow"
      (btu/context-assoc! :workflow-title (str "test-workflow-create-edit " (btu/get-seed)))
      (btu/scroll-and-click :create-workflow)
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Create workflow"}))
      (btu/wait-page-loaded)
      (select-option "Organization" "nbn")
      (fill-form-field "Title" (btu/context-get :workflow-title))
      (btu/scroll-and-click :type-decider)
      (btu/wait-page-loaded)
      (select-option "Handlers" "handler")
      (select-option "Handlers" "carl")
      (select-option "Forms" "Simple form")
      (btu/screenshot "test-workflow-create-edit-1.png")
      (btu/scroll-and-click :save))
    (testing "view workflow"
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Workflow"}))
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-2.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "NBN"
              "Title" (btu/context-get :workflow-title)
              "Type" "Decider workflow"
              "Handlers" "Carl Reviewer (carl@example.com), Hannah Handler (handler@example.com)"
              "Forms" "Simple form"
              "Active" true}
             (slurp-fields :workflow))))
    (testing "edit workflow"
      (btu/scroll-and-click {:fn/has-class :edit-workflow})
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Edit workflow"}))
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-3.png")
      (select-option "Organization" "Default")
      (fill-form-field "Title" " v2") ;; fill-form-field appends text to existing value
      (is (btu/disabled? :type-default)) ;; can't change type
      ;; removing an item is hard to script reliably, so let's just add one
      (select-option "Handlers" "reporter")
      (is (= "Simple form" (btu/get-element-text {:tag :div :id :workflow-forms}))) ; readonly field
      (btu/screenshot "test-workflow-create-edit-4.png")
      (btu/scroll-and-click :save))
    (testing "view workflow again"
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Workflow"}))
      (btu/wait-page-loaded)
      (btu/screenshot "test-workflow-create-edit-5.png")
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (= {"Organization" "The Default Organization"
              "Title" (str (btu/context-get :workflow-title) " v2")
              "Type" "Decider workflow"
              "Handlers" "Carl Reviewer (carl@example.com), Hannah Handler (handler@example.com), Reporter (reporter@example.com)"
              "Forms" "Simple form"
              "Active" true}
             (slurp-fields :workflow)))
      (is (btu/visible? {:tag :a :fn/text "Simple form"})))))

(deftest test-blacklist
  (btu/with-postmortem
    (testing "set up resource & user"
      (test-helpers/create-resource! {:resource-ext-id "blacklist-test"})
      (users/add-user! {:userid "baddie" :name "Bruce Baddie" :email "bruce@example.com"}))
    (testing "add blacklist entry via resource page"
      (login-as "owner")
      (go-to-admin "Resources")
      (click-row-action [:resources] {:fn/text "blacklist-test"}
                        (select-button-by-label "View"))
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Resource"}))
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? :blacklist))
      (is (= [{}] (slurp-rows :blacklist)))
      (btu/fill-human :blacklist-user "baddie\n")
      (btu/fill-human :blacklist-comment "This is a test.")
      (btu/screenshot "test-blacklist-1.png")
      (btu/scroll-and-click :blacklist-add)
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))
    (testing "check entry on resource page"
      (is (btu/eventually-visible? :blacklist))
      (is (= [{} ;; TODO remove the header row in slurp-rows
              {"resource" "blacklist-test"
               "user" "Bruce Baddie"
               "userid" "baddie"
               "email" "bruce@example.com"
               "added-by" "Owner"
               "comment" "This is a test."
               "commands" "Remove"}]
             (mapv #(dissoc % "added-at") (slurp-rows :blacklist)))))
    (testing "check entry on blacklist page"
      (go-to-admin "Blacklist")
      (is (btu/eventually-visible? {:tag :h1 :fn/text "Blacklist"}))
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? :blacklist))
      (is (= [{}
              {"resource" "blacklist-test"
               "user" "Bruce Baddie"
               "userid" "baddie"
               "email" "bruce@example.com"
               "added-by" "Owner"
               "comment" "This is a test."
               "commands" "Remove"}]
             (mapv #(dissoc % "added-at") (slurp-rows :blacklist)))))
    (testing "remove entry"
      (click-row-action [:blacklist] {:fn/text "baddie"}
                        (select-button-by-label "Remove"))
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))
      (is (btu/eventually-visible? :blacklist))
      (is (= [{}] (slurp-rows :blacklist))))))

(deftest test-report
  (btu/with-postmortem
    (testing "set up form and submit an application using it"
      (btu/context-assoc! :form-title (str "Reporting Test Form " (btu/get-seed)))
      (btu/context-assoc! :form-id (test-helpers/create-form! {:form/internal-name (btu/context-get :form-title)
                                                               :form/external-title {:en (str (btu/context-get :form-title) " EN")
                                                                                     :fi (str (btu/context-get :form-title) " FI")
                                                                                     :sv (str (btu/context-get :form-title) " SV")}
                                                               :form/fields [{:field/id "desc"
                                                                              :field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                              :field/optional false
                                                                              :field/type :description}]}))
      (btu/context-assoc! :workflow-id (test-helpers/create-workflow! {:handlers ["handler"]}))
      (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id) :workflow-id (btu/context-get :workflow-id)}))

      (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                      [(btu/context-get :catalogue-id)]
                                                                      (str "test-reporting " (btu/get-seed))))
      (test-helpers/command! {:type :application.command/save-draft
                              :application-id (btu/context-get :application-id)
                              :field-values [{:form (btu/context-get :form-id)
                                              :field "desc"
                                              :value "Tämä on monimutkainen arvo skandein varusteltuna!"}]
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/submit
                              :application-id (btu/context-get :application-id)
                              :actor "alice"})

      (btu/delete-downloaded-files! #"applications_.*\.csv")) ; make sure no report exists

    (testing "open report"
      (login-as "reporter")
      (go-to-admin "Reports")
      (btu/scroll-and-click :export-applications-button)
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? {:tag :label :fn/text "Form"}))
      (select-option* "Form" (btu/context-get :form-title))
      (btu/scroll-and-click :export-applications-button)
      (btu/wait-for-downloads #"applications_.*\.csv")) ; report has time in it that is difficult to control

    (testing "check report CSV"
      (let [application (get-application-from-api (btu/context-get :application-id))
            q (fn [s] (str "\"" s "\""))]
        (is (= ["\"Id\",\"External id\",\"Applicant\",\"Submitted\",\"State\",\"Resources\",\"description\""
                (str/join ","
                          [(:application/id application)
                           (q (:application/external-id application))
                           (q (get-in application [:application/applicant :name]))
                           (q (text/localize-time (get-in application [:application/first-submitted])))
                           (q "Applied")
                           (q "")
                           (q "Tämä on monimutkainen arvo skandein varusteltuna!")])]
               (->> #"applications_.*\.csv"
                    btu/downloaded-files
                    first
                    slurp
                    str/split-lines)))))))

(defn- get-organization-last-modified [organization-id]
  (text/localize-time (:organization/last-modified (organizations/get-organization-raw {:organization/id organization-id}))))

(deftest test-organizations
  (test-helpers/create-user! {:eppn "organization-owner1" :commonName "Organization Owner 1"
                              :mail "organization-owner1@example.com" :organizations [{:organization/id "Default"}]} :owner)
  (test-helpers/create-user! {:eppn "organization-owner2" :commonName "Organization Owner 2"
                              :mail "organization-owner2@example.com" :organizations [{:organization/id "Default"}]} :owner)

  (btu/with-postmortem
    (login-as "owner")
    (go-to-admin "Organizations")

    (testing "create"
      (btu/scroll-and-click :create-organization)
      (btu/context-assoc! :organization-id (str "Organization id " (btu/get-seed)))
      (btu/context-assoc! :organization-name (str "Organization " (btu/get-seed)))
      (is (btu/eventually-visible? :id))
      (btu/fill-human :id (btu/context-get :organization-id))
      (btu/fill-human :short-name-en "SNEN")
      (btu/fill-human :short-name-fi "SNFI")
      (btu/fill-human :short-name-sv "SNSV")
      (btu/fill-human :name-en (str (btu/context-get :organization-name) " EN"))
      (btu/fill-human :name-fi (str (btu/context-get :organization-name) " FI"))
      (btu/fill-human :name-sv (str (btu/context-get :organization-name) " SV"))
      (select-option* "Owners" "Organization owner 1")
      (btu/scroll-and-click :add-review-email)
      (btu/scroll-and-click :add-review-email)

      (is (btu/eventually-visible? :review-emails-1-name-en))
      (btu/fill-human :review-emails-1-name-en "Review mail EN") ; fill second
      (btu/fill-human :review-emails-1-name-fi "Review mail FI")
      (btu/fill-human :review-emails-1-name-sv "Review mail SV")
      (btu/fill-human :review-emails-1-email "review.email@example.com")
      (btu/scroll-and-click {:css ".remove"}) ; remove first
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success")))

    (testing "view after creation"
      (is (btu/eventually-visible? :organization))
      (is (= {"Id" (btu/context-get :organization-id)
              "Short name (FI)" "SNFI"
              "Short name (EN)" "SNEN"
              "Short name (SV)" "SNSV"
              "Title (EN)" (str (btu/context-get :organization-name) " EN")
              "Title (FI)" (str (btu/context-get :organization-name) " FI")
              "Title (SV)" (str (btu/context-get :organization-name) " SV")
              "Owners" "Organization Owner 1 (organization-owner1@example.com)"
              "Name (FI)" "Review mail FI"
              "Name (SV)" "Review mail SV"
              "Name (EN)" "Review mail EN"
              "Email" "review.email@example.com"
              "Active" true
              "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
              "Modifier" "Owner (owner@example.com)"}
             (slurp-fields :organization))))

    (testing "edit after creation"
      (btu/scroll-and-click :edit-organization)
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? :short-name-en))
      (select-option* "Owners" "Organization owner 2")
      (btu/clear :short-name-en)
      (btu/fill-human :short-name-en "SNEN2")
      (btu/clear :short-name-fi)
      (btu/fill-human :short-name-fi "SNFI2")
      (btu/clear :short-name-sv)
      (btu/fill-human :short-name-sv "SNSV2")
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:css ".alert-success"}))
      (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))

      (testing "view after editing"
        (is (btu/eventually-visible? :organization))
        (is (= {"Id" (btu/context-get :organization-id)
                "Short name (FI)" "SNFI2"
                "Short name (EN)" "SNEN2"
                "Short name (SV)" "SNSV2"
                "Title (EN)" (str (btu/context-get :organization-name) " EN")
                "Title (FI)" (str (btu/context-get :organization-name) " FI")
                "Title (SV)" (str (btu/context-get :organization-name) " SV")
                "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                "Name (FI)" "Review mail FI"
                "Name (SV)" "Review mail SV"
                "Name (EN)" "Review mail EN"
                "Email" "review.email@example.com"
                "Active" true
                "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                "Modifier" "Owner (owner@example.com)"}
               (slurp-fields :organization)))))

    (testing "use after creation"
      (go-to-admin "Resources")
      (btu/wait-page-loaded)
      (btu/scroll-and-click :create-resource)
      (btu/wait-page-loaded)
      (is (btu/eventually-visible? :organization))
      (btu/fill-human :resid (str "resource for " (btu/context-get :organization-name)))
      (select-option* "Organization" (btu/context-get :organization-name))
      (btu/scroll-and-click :save)
      (is (btu/eventually-visible? {:css ".alert-success"})))

    (testing "as organization owner"
      (logout)
      (login-as "organization-owner2")
      (go-to-admin "Organizations")

      (testing "list shows created organization"
        (is (btu/eventually-visible? :organizations))
        (let [orgs (slurp-rows :organizations)]
          (is (some #{{"short-name" "SNEN2"
                       "name" (str (btu/context-get :organization-name) " EN")
                       "active" true
                       "commands" "ViewDisableArchive"}}
                    orgs))))

      (testing "view from list"
        (click-row-action [:organizations]
                          {:fn/text (str (btu/context-get :organization-name) " EN")}
                          (select-button-by-label "View"))
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? :organization))
        (is (= {"Id" (btu/context-get :organization-id)
                "Short name (FI)" "SNFI2"
                "Short name (EN)" "SNEN2"
                "Short name (SV)" "SNSV2"
                "Title (EN)" (str (btu/context-get :organization-name) " EN")
                "Title (FI)" (str (btu/context-get :organization-name) " FI")
                "Title (SV)" (str (btu/context-get :organization-name) " SV")
                "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                "Name (FI)" "Review mail FI"
                "Name (SV)" "Review mail SV"
                "Name (EN)" "Review mail EN"
                "Email" "review.email@example.com"
                "Active" true
                "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                "Modifier" "Owner (owner@example.com)"}
               (slurp-fields :organization))))

      (testing "edit as organization owner"
        (btu/scroll-and-click :edit-organization)
        (btu/wait-page-loaded)
        (is (btu/eventually-visible? :short-name-en))
        (btu/clear :short-name-en)
        (btu/fill-human :short-name-en "SNEN")
        (btu/clear :short-name-fi)
        (btu/fill-human :short-name-fi "SNFI")
        (btu/clear :short-name-sv)
        (btu/fill-human :short-name-sv "SNSV")
        (btu/scroll-and-click :save)
        (is (btu/eventually-visible? {:css ".alert-success"}))
        (is (str/includes? (btu/get-element-text {:css ".alert-success"}) "Success"))

        (testing "view after editing"
          (is (btu/eventually-visible? :organization))
          (is (= {"Id" (btu/context-get :organization-id)
                  "Short name (FI)" "SNFI"
                  "Short name (EN)" "SNEN"
                  "Short name (SV)" "SNSV"
                  "Title (EN)" (str (btu/context-get :organization-name) " EN")
                  "Title (FI)" (str (btu/context-get :organization-name) " FI")
                  "Title (SV)" (str (btu/context-get :organization-name) " SV")
                  "Owners" "Organization Owner 1 (organization-owner1@example.com)\nOrganization Owner 2 (organization-owner2@example.com)"
                  "Name (FI)" "Review mail FI"
                  "Name (SV)" "Review mail SV"
                  "Name (EN)" "Review mail EN"
                  "Email" "review.email@example.com"
                  "Active" true
                  "Last modified" (get-organization-last-modified (btu/context-get :organization-id))
                  "Modifier" "Organization Owner 2 (organization-owner2@example.com)"}
                 (slurp-fields :organization))))))))

(deftest test-small-navbar
  (testing "create a test application with the API to have another page to navigate to"
    (btu/context-assoc! :form-id (test-helpers/create-form! {:form/fields [{:field/title {:en "description" :fi "kuvaus" :sv "rubrik"}
                                                                            :field/optional false
                                                                            :field/type :description}]}))
    (btu/context-assoc! :catalogue-id (test-helpers/create-catalogue-item! {:form-id (btu/context-get :form-id)}))
    (btu/context-assoc! :application-id (test-helpers/create-draft! "alice"
                                                                    [(btu/context-get :catalogue-id)]
                                                                    "test-small-navbar"))
    (test-helpers/command! {:type :application.command/submit
                            :application-id (btu/context-get :application-id)
                            :actor "alice"}))
  (btu/with-postmortem
    (login-as "alice")
    (go-to-catalogue)
    (btu/set-window-size 400 600) ; small enough for mobile
    (btu/wait-invisible :small-navbar)
    (btu/scroll-and-click {:css ".navbar-toggler"})
    (is (btu/eventually-visible? :small-navbar))
    (btu/screenshot "small-navbar.png")
    (btu/scroll-and-click [:small-navbar {:tag :a :fn/text "Applications"}])
    (btu/wait-invisible :small-navbar) ; menu should be hidden
    (is (btu/eventually-visible? {:tag :h1 :fn/text "Applications"}))
    (btu/scroll-and-click {:css ".navbar-toggler"})
    (btu/scroll-and-click [:small-navbar {:tag :button :fn/text "FI"}])
    (btu/wait-invisible :small-navbar) ; menu should be hidden
    (is (btu/eventually-visible? {:tag :h1 :fn/text "Hakemukset"}))
    (user-settings/delete-user-settings! "alice") ; clear language settings
    (is true)))  ; avoid no assertions warning
