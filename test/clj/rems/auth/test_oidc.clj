(ns ^:integration rems.auth.test-oidc
  (:require [clj-http.client]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.api.testing :refer [api-fixture]]
            [rems.auth.oidc :as oidc]
            [rems.config]
            [rems.ga4gh]
            [rems.jwt]
            [rems.json :as json]
            [rems.testing-util :refer [with-fake-login-users]]))

(defn- with-special-setup [params f]
  (let [id-data (:id-data params)
        config (:config params)
        user-info {:unrelated 42}]

    (with-redefs [rems.config/env (merge (assoc rems.config/env
                                                :oidc-userid-attributes [{:attribute "sub" :rename "elixirId"}
                                                                         {:attribute "old_sub"}]
                                                :log-authentication-details false
                                                :public-url "http://special:3000/"
                                                :oidc-client-id "special.client-id"
                                                :oidc-client-secret "special.client-secret")
                                         config)
                  rems.config/oidc-configuration {:token_endpoint "https://special.case/token"
                                                  :issuer "https://special.case/issuer"
                                                  :userinfo_endpoint "https://special.case/user-info"}
                  clj-http.client/post (fn [url request] ; fetch id-token
                                         (is (= "https://special.case/token" url))
                                         (is (= {:basic-auth ["special.client-id" "special.client-secret"]
                                                 :form-params {:grant_type "authorization_code"
                                                               :code "special-case-code"
                                                               :redirect_uri "http://special:3000/oidc-callback"}
                                                 :save-request? false
                                                 :debug-body false}
                                                request))
                                         {:body (json/generate-string {:access_token "special.access-token"
                                                                       :id_token "special.id-token"})})
                  rems.jwt/validate (fn [id-token issuer audience _now] ; id token validation
                                      (is (= "special.id-token" id-token))
                                      (is (= "https://special.case/issuer" issuer))
                                      (is (= "special.client-id" audience))
                                      id-data)
                  clj-http.client/get (fn [url request] ; user-info
                                        (is (= "https://special.case/user-info" url))
                                        (is (= {:headers {"Authorization" "Bearer special.access-token"}}
                                               request))
                                        {:body (json/generate-string user-info)})
                  rems.ga4gh/passport->researcher-status-by (fn [id-token]
                                                              (is (= user-info id-token))
                                                              id-token)]
      (with-fake-login-users {} (f)))))

(use-fixtures
  :once
  api-fixture)

(deftest test-user-does-not-exist
  (with-special-setup {:id-data {:sub "does-not-exist" :name "Does Not Exist" :email "does-not-exist@example.com"}}
    (fn []
      (let [request {:params {:code "special-case-code"}}
            response (oidc/oidc-callback request)]
        (is (= {:status 302
                :headers {"Location" "/redirect"}
                :body ""
                :session
                {:access-token "special.access-token"
                 :identity {:userid "does-not-exist" :name "Does Not Exist" :email "does-not-exist@example.com"}}}
               response)
            "created and allowed in")))))

(deftest test-user-has-no-details
  (testing "default is to check the name only"
    (with-special-setup {:id-data {:sub "has-no-details"}}
      (fn []
        (try
          (oidc/oidc-callback {:params {:code "special-case-code"}})
          (catch clojure.lang.ExceptionInfo e
            (is (= {:key :t.login.errors/invalid-user
                    :args [:t.login.errors/name]
                    :user {:userid "has-no-details"
                           :name nil
                           :email nil}}
                   (ex-data e))))))))
  (testing "validation can be configured"
    (with-special-setup {:id-data {:sub "has-no-details"}
                         :config {:oidc-require-email true}}
      (fn []
        (try
          (oidc/oidc-callback {:params {:code "special-case-code"}})
          (catch clojure.lang.ExceptionInfo e
            (is (= {:key :t.login.errors/invalid-user
                    :args [:t.login.errors/name :t.login.errors/email]
                    :user {:userid "has-no-details"
                           :name nil
                           :email nil}}
                   (ex-data e)))))))))

(deftest test-no-code
  (with-special-setup {:id-data {:sub "user" :name "User" :email "user@example.com"}}
    (fn []
      (let [request {}
            response (oidc/oidc-callback request)]
        (is (= {:status 302
                :headers {"Location" "/error?key=:t.login.errors/unknown"}
                :body ""}
               response)
            "can't log in with missing code parameter in callback"))

      (let [request {:params {:code ""}}
            response (oidc/oidc-callback request)]
        (is (= {:status 302
                :headers {"Location" "/error?key=:t.login.errors/unknown"}
                :body ""}
               response)
            "can't log in with blank code parameter in callback")))))

(deftest test-error
  (with-special-setup {:id-data {:sub "user" :name "User" :email "user@example.com"}}
    (fn []
      (let [request {:params {:error "failed"}}
            response (oidc/oidc-callback request)]
        (is (= {:status 302
                :headers {"Location" "/error?key=:t.login.errors/unknown"}
                :body ""}
               response)
            "can't log in when an error happens")))))
