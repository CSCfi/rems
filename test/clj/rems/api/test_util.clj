(ns ^:integration rems.api.test-util
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [compojure.api.sweet :refer [GET]]
            [rems.api.util]
            [rems.api.testing :refer [add-multipart api-fixture add-login-cookies assert-response-is-ok read-body]]
            [rems.context :as context]
            [rems.handler :refer [handler]]
            [rems.service.test-data :as test-data]
            [ring.util.http-response :refer [ok]]
            [ring.mock.request :refer [header json-body request]]
            [rems.db.test-data-helpers :as test-helpers]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (rems.auth ForbiddenException UnauthorizedException)))

(use-fixtures
  :each
  api-fixture
  ;; TODO should this fixture have a name?
  (fn [f]
    (test-data/create-test-api-key!)
    (test-data/create-test-users-and-roles!)
    (f)))

(deftest test-route-role-check
  (testing "no roles required"
    (let [route (GET "/foo" []
                  :summary "Summary text"
                  (ok {:success true}))]
      (is (= {:status 200
              :headers {}
              :body {:success true}}
             (route {:request-method :get
                     :uri "/foo"})))))

  (testing "role required"
    (let [route (GET "/foo" []
                  :summary "Summary text"
                  :roles #{:role1}
                  (ok {:success true}))]

      (testing "and user has it"
        (binding [context/*roles* #{:role1}
                  context/*user* {:userid "user1"}]
          (is (= {:status 200
                  :headers {}
                  :body {:success true}}
                 (route {:request-method :get
                         :uri "/foo"})))))

      (testing "but user doesn't have it"
        (binding [context/*roles* #{}
                  context/*user* {:userid "user1"}]
          (is (thrown? ForbiddenException
                       (route {:request-method :get
                               :uri "/foo"})))))

      (testing "but user is not logged in"
        (binding [context/*roles* #{:role1}
                  context/*user* nil]
          (is (thrown? UnauthorizedException
                       (route {:request-method :get
                               :uri "/foo"})))))))

  (testing "required roles are added to summary documentation"
    (let [route (GET "/foo" []
                  :summary "Summary text"
                  :roles #{:role1 :role2}
                  (ok {:success true}))]
      (is (= "Summary text (roles: role1, role2)"
             (get-in route [:info :public :summary])))))

  (testing "roles can be a var"
    (let [route (GET "/foo" []
                  :summary "Summary text"
                  :roles #{:role1}
                  (ok {:success true}))]
      (is (= "Summary text (roles: role1)"
             (get-in route [:info :public :summary])))
      (binding [context/*roles* #{:role1}
                context/*user* {:userid "user1"}]
        (is (= {:status 200
                :headers {}
                :body {:success true}}
               (route {:request-method :get
                       :uri "/foo"}))))
      (binding [context/*roles* #{}
                context/*user* {:userid "user1"}]
        (is (thrown? ForbiddenException
                     (route {:request-method :get
                             :uri "/foo"}))))))

  (testing "summary documentation is required"
    (try
      (eval
       `(GET "/foo" []
          :roles #{:some-role}
          (ok {:success true})))
      (is false "did not throw")
      (catch clojure.lang.Compiler$CompilerException ce
        (is (-> ce
                .getCause
                .getMessage
                (.contains "Route must have a :summary when using :roles and it must be specified before :roles")))))))

(deftest test-extended-logging
  (let [log (atom nil)
        log* log/log*]
    (with-redefs [rems.config/env (merge rems.config/env {:enable-extended-logging true})
                  log/log* (fn [logger level throwable message]
                             (log* logger level throwable message)
                             (when (str/includes? message "params:")
                               (reset! log [level message])))]
      (let [cat-id (test-helpers/create-catalogue-item! {})
            body (-> (request :post "/api/applications/create?foo=bar")
                     (add-login-cookies "alice")
                     (json-body {:catalogue-item-ids [cat-id]})
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:success body))
        (is (= [:info (str "> params: {:foo bar, :catalogue-item-ids [" cat-id "]}")] @log))

        ;; this uses the command API through a macro
        (let [app-id (:application-id body)
              body (-> (request :post "/api/applications/save-draft")
                       (add-login-cookies "alice")
                       (json-body {:application-id app-id
                                   :field-values [{:field "fld1" :form 1 :value "nonexistent"}]})
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (:success body))
          (is (= [:info (str "> params: {:application-id " app-id ", :field-values [{:value nonexistent, :field fld1, :form 1}]}")] @log)))

        ;; check that the attachment content is not logged
        (let [app-id (:application-id body)
              testfile (io/file "./test-data/test.txt")
              body (with-redefs [rems.api.util/select-filenames #(assoc % :tempfile "ring-would-name-this-file-randomly")]
                     (-> (request :post (str "/api/applications/add-attachment?application-id=" app-id))
                         (add-multipart {:file testfile})
                         (add-login-cookies "alice")
                         handler
                         assert-response-is-ok
                         read-body))]
          (is (:success body))
          (is (= [:info (str "> params: {:application-id " app-id ", :file {:filename test.txt, :content-type text/plain, :tempfile ring-would-name-this-file-randomly, :size 16}}")] @log))))

      (let [body (-> (request :post "/api/resources/create")
                     (add-login-cookies "owner")
                     (json-body {:resid "extended-logging"
                                 :organization {:organization/id "nbn"}
                                 :licenses []})
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:success body))
        (is (= [:info "> params: {:licenses [], :organization #:organization{:id nbn}, :resid extended-logging}"] @log))))))
