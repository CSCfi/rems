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

(use-fixtures :once api-fixture)

(deftest test-user-does-not-exist
  (let [id-data {:sub "does-not-exist" :name "Does Not Exist"}
        user-info {:unrelated 42}]

    (with-redefs [rems.config/env (assoc rems.config/env
                                         :oidc-userid-attributes [{:attribute "sub" :rename "elixirId"}
                                                                  {:attribute "old_sub"}]
                                         :log-authentication-details false
                                         :oidc-client-id "special.client-id"
                                         :oidc-client-secret "special.client-secret")
                  rems.config/oidc-configuration {:token_endpoint "https://special.case/token"
                                                  :issuer "https://special.case/issuer"
                                                  :userinfo_endpoint "https://special.case/user-info"}
                  clj-http.client/post (fn [url request] ; fetch id-token
                                         (is (= "https://special.case/token" url))
                                         (is (= {:basic-auth ["special.client-id" "special.client-secret"]
                                                 :form-params {:grant_type "authorization_code"
                                                               :code "special-case-code"
                                                               :redirect_uri "http://localhost:3000/oidc-callback"}
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
      (with-fake-login-users {"alice" {:sub "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                              "elixir-alice" {:sub "elixir-alice" :old_sub "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
        (testing "try to log in does-not-exist"
          (let [request {:params {:code "special-case-code"}}
                response (oidc/oidc-callback request)]
            (is (= {:status 302
                    :headers {"Location" "/redirect"}
                    :body ""
                    :session
                    {:access-token "special.access-token"
                     :identity {:eppn "does-not-exist" :commonName "Does Not Exist" :mail nil}}}
                   response)
                "created and allowed in")))))))

(deftest test-user-has-no-name
  (let [id-data {:sub "does-not-exist"}
        user-info {:unrelated 42}]

    (with-redefs [rems.config/env (assoc rems.config/env
                                         :oidc-userid-attributes [{:attribute "sub" :rename "elixirId"}
                                                                  {:attribute "old_sub"}]
                                         :log-authentication-details false
                                         :oidc-client-id "special.client-id"
                                         :oidc-client-secret "special.client-secret")
                  rems.config/oidc-configuration {:token_endpoint "https://special.case/token"
                                                  :issuer "https://special.case/issuer"
                                                  :userinfo_endpoint "https://special.case/user-info"}
                  clj-http.client/post (fn [url request] ; fetch id-token
                                         (is (= "https://special.case/token" url))
                                         (is (= {:basic-auth ["special.client-id" "special.client-secret"]
                                                 :form-params {:grant_type "authorization_code"
                                                               :code "special-case-code"
                                                               :redirect_uri "http://localhost:3000/oidc-callback"}
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
      (with-fake-login-users {"alice" {:sub "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                              "elixir-alice" {:sub "elixir-alice" :old_sub "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
        (testing "try to log in does-not-exist"
          (let [request {:params {:code "special-case-code"}}
                response (oidc/oidc-callback request)]
            (is (= {:status 302
                    :headers {"Location" "/redirect"}
                    :body ""
                    :session
                    {:access-token "special.access-token"
                     :identity {:eppn "does-not-exist" :commonName nil :mail nil}}}
                   response)
                "created and allowed in")))))))
