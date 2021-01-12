(ns rems.test-jwt
  (:require [clojure.test :refer :all]
            [rems.jwt :as jwt]))

;; key cached from https://luontola.eu.auth0.com/.well-known/jwks.json
(def jwk {:alg "RS256",
          :kty "RSA",
          :use "sig",
          :x5c ["MIIC8jCCAdqgAwIBAgIJHoFouif+0twQMA0GCSqGSIb3DQEBBQUAMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTAeFw0xNjA5MTYwODM4MjhaFw0zMDA1MjYwODM4MjhaMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOAvQieNzD29VsOdQc3YHPzpLkNkShpeMuFYB76WHRb6UQpUBAKSEVpxvu1G0DG2shMJ+DObsQ81ID+WFYW445Dz6sJE4dRGmSx9oEGPB7kiDGZx1bb2O14n6v17/qzz2PHgCT05BIU+AmrpN5GNZdnJya0jU4r0UQInDRD5/qZwUF8oXfcG7eewcYLak7ZwsjA1Kf4HADkMIZo8NZ+9TtvN2cToPzPtlGSInsjW7oZP1m/qO4xvEyAQUtj11QV8so9F5NPyd9h5PYlo5t792I4bOUykpck1KR81RUJuZ3HLt5104JNFYcEe2tjnt9DtBAXfMvMtdiJZ85BRE9XJ9NMCAwEAAaMvMC0wDAYDVR0TBAUwAwEB/zAdBgNVHQ4EFgQUj8UVIFeOuo6D0UE3eogA7Ht623cwDQYJKoZIhvcNAQEFBQADggEBAJfJ5yi3Akh9pGD+PMiN0AqzT9kJr6g6Z3EZJ0qP6iiGZLsMBSDeulpjWMOeYvbW0OCKJI8X+YidXAlxOGNyIxkyu8IXJ7E5Z+DSg4H9D6YG26VQKqKsorhZ2YxRsckagaMEYqH7KIKesS5iiK32ULR5iV5+NdBGafoNLNwBxX6Pge1f2QskJJy22vWlh9NA2jmBbCIl5OzNxEouMn34jCnq/F+zg0fDEAOM9ZdcsjXRMT3a2Dta7L4G9bnkX8a9gGe6cRcqINeaIMY4/Jpp6Lb6t1lvWYG+TbhWAoeHl3ZfqjNm4cnnvoNAkiVLC73rC7SHhzzyKDwZS8p31QtEB1E="],
          :n "4C9CJ43MPb1Ww51Bzdgc_OkuQ2RKGl4y4VgHvpYdFvpRClQEApIRWnG-7UbQMbayEwn4M5uxDzUgP5YVhbjjkPPqwkTh1EaZLH2gQY8HuSIMZnHVtvY7Xifq_Xv-rPPY8eAJPTkEhT4Cauk3kY1l2cnJrSNTivRRAicNEPn-pnBQXyhd9wbt57BxgtqTtnCyMDUp_gcAOQwhmjw1n71O283ZxOg_M-2UZIieyNbuhk_Wb-o7jG8TIBBS2PXVBXyyj0Xk0_J32Hk9iWjm3v3Yjhs5TKSlyTUpHzVFQm5nccu3nXTgk0VhwR7a2Oe30O0EBd8y8y12IlnzkFET1cn00w",
          :e "AQAB",
          :kid "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA",
          :x5t "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA"})

(def jwks {:keys [jwk]})

(def token "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlJqWTFNekEzTlRKR1JrTTFRVGt5TlVaRk1UazNOa1UyT1Rjd1FVRXdSakV6TWpSQ1FUQkNOQSJ9.eyJnaXZlbl9uYW1lIjoiRXNrbyIsImZhbWlseV9uYW1lIjoiTHVvbnRvbGEiLCJuaWNrbmFtZSI6ImVza28ubHVvbnRvbGEiLCJuYW1lIjoiRXNrbyBMdW9udG9sYSIsInBpY3R1cmUiOiJodHRwczovL2xoNi5nb29nbGV1c2VyY29udGVudC5jb20vLUFtRHYtVlZoUUJVL0FBQUFBQUFBQUFJL0FBQUFBQUFBQWVJL2JIUDhsVk5ZMWFBL3Bob3RvLmpwZyIsImxvY2FsZSI6ImVuLUdCIiwidXBkYXRlZF9hdCI6IjIwMTgtMTEtMDNUMTY6MTY6NDcuNTAyWiIsImlzcyI6Imh0dHBzOi8vbHVvbnRvbGEuZXUuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTAyODgzMjM3Nzk0NDUxMTExNDU5IiwiYXVkIjoiOHRWa2Rmbnc4eW5aNnJYTm5kRDZlWjZFcnNIZElnUGkiLCJpYXQiOjE1NDEyNjE4MDcsImV4cCI6MTU0MTI5NzgwNywibm9uY2UiOiI5UGlFemxtTDk3d0xudTFBLVVPakZ-VFAyekVYU3dvLSJ9.v3avK87uM7ncT3Bx8aJ7NbbCaOjgv_TQ9lRR6hs6CTFteA3yhbZpX0isB3_2Lxf46AsqVEWWFvY-Afslc_32_UzLfaWEPH5HwQwRAwUW9m34tx-RYhNtP02jFAmJIZG-akhz0TYlEzcblU1tOKJbLFuVHyRAOWKRSvlJXioVDfqEdsApNAI78-aoEjhf3ouLzDQVl15AfPBP8Czmp2wmwRfD_2ES66e-_q7cm9zzkcWTjub0wLmiNhDCQfnZJxfA9r5XUQLThbUFHHPnSx-QfLqP8tXmMLm9B9BV8J7G1humG8gaCycq_Q-9ieSDAjpvZ8C5ePTNLVOga4j-MaaFtA")

(def jwt-issuer "https://luontola.eu.auth0.com/")
(def jwt-audience "8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi")
(def now #inst "2018-11-03T16:17:00.000Z")

(def jwt-encoded "eyJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiMTIzIn0.6I9x2mlI2QZSk0E3xZPzlKBMSEsXgYv_Lgoz0yh7S7w")

(deftest test-jwk-sign
  (testing "signs valid payload"
    (is (= jwt-encoded
           (jwt/sign {:name "123"} "secret")))))

(deftest test-jwt-validate
  (with-redefs [jwt/fetch-jwks (constantly jwks)]
    (testing "decodes valid tokens"
      (is (= {:name "Esko Luontola"}
             (select-keys (jwt/validate token jwt-issuer jwt-audience now) [:name]))))

    ;; What needs to be validated from a JWT token
    ;; https://auth0.com/docs/tokens/id-token#validate-an-id-token

    (testing "verifies token signature"
      (is (thrown-with-msg? Exception #"Message seems corrupt or manipulated"
                            (jwt/validate (clojure.string/replace token #"A$" "a") jwt-issuer jwt-audience now))))

    (testing "validates token expiration"
      (is (thrown-with-msg? Exception #"Token is expired"
                            (jwt/validate token jwt-issuer jwt-audience #inst "2018-11-04T16:17:00.000Z"))))

    (testing "validates token issuer"
      (is (thrown-with-msg? Exception #"Issuer does not match x"
                            (jwt/validate token "x" jwt-audience now))))

    (testing "validates token audience"
      (is (thrown-with-msg? Exception #"Audience does not match x"
                            (jwt/validate token jwt-issuer "x" now))))

    (testing "issuer and audience validation are optional"
      (is (= {:name "Esko Luontola"}
             (select-keys (jwt/validate token nil nil now) [:name]))))))
