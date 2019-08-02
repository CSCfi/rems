(ns rems.api.test-util
  (:require [clojure.test :refer :all]
            [compojure.api.sweet :refer :all]
            [rems.api.util :refer :all]
            [rems.context :as context]
            [ring.util.http-response :refer :all])
  (:import (rems.auth ForbiddenException NotAuthorizedException)))

(deftest route-role-check-test
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
                  context/*user* {:eppn "user1"}]
          (is (= {:status 200
                  :headers {}
                  :body {:success true}}
                 (route {:request-method :get
                         :uri "/foo"})))))

      (testing "but user doesn't have it"
        (binding [context/*roles* #{}
                  context/*user* {:eppn "user1"}]
          (is (thrown? ForbiddenException
                       (route {:request-method :get
                               :uri "/foo"})))))

      (testing "but user is not logged in"
        (binding [context/*roles* #{:role1}
                  context/*user* nil]
          (is (thrown? NotAuthorizedException
                       (route {:request-method :get
                               :uri "/foo"})))))))

  (testing "required roles are added to summary documentation"
    (let [route (GET "/foo" []
                  :summary "Summary text"
                  :roles #{:role1 :role2}
                  (ok {:success true}))]
      (is (= "Summary text (roles: role1, role2)"
             (get-in route [:info :public :summary])))))

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
