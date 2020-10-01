(ns ^:integration rems.api.test-audit-log
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :each api-fixture)

(deftest test-audit-log
  (let [time-a (atom nil)
        app-id (test-helpers/create-application! {:actor "alice"})]

    (test-helpers/command! {:type           :application.command/submit
                            :application-id app-id
                            :actor          "alice"})

    (testing "populate log"
      (testing "> unknown endpoint"
        (testing "> no user"
          (is (response-is-not-found? (-> (request :get "/api/unknown")
                                          handler))))
        (testing "> valid api-key and user"
          (is (response-is-not-found? (-> (request :get "/api/unknown")
                                          (authenticate "42" "owner")
                                          handler)))))
      (testing "> known endpoint"
        (testing "> api key"
          (testing "> GET"
            (testing "> unauthorized"
              (is (response-is-forbidden? (-> (request :get "/api/users/active")
                                              (authenticate "42" "alice")
                                              handler))))
            (testing "> authorized"
              (is (response-is-ok? (-> (request :get "/api/users/active")
                                       (authenticate "42" "owner")
                                       handler))))
            (Thread/sleep 2)
            (reset! time-a (time/now))
            (testing "> application"
              (is (response-is-ok? (-> (request :get (str "/api/applications/" app-id))
                                       (authenticate "42" "alice")
                                       handler)))
              (is (response-is-ok? (-> (request :get (str "/api/applications/" app-id "/pdf"))
                                       (authenticate "42" "reporter")
                                       handler)))))
          (testing "> POST"
            (testing "> status 200, different api key"
              (api-key/add-api-key! "43" {})
              ;; this is actually a {:success false} response since
              ;; the application doesn't exist, but here we only care
              ;; about the HTTP status.
              (is (response-is-ok? (-> (request :post "/api/applications/submit")
                                       (authenticate "43" "alice")
                                       (json-body {:application-id 99999999999})
                                       handler))))
            (testing "> status 400"
              (is (response-is-bad-request? (-> (request :post "/api/applications/submit")
                                                (authenticate "42" "alice")
                                                (json-body {:boing "3"})
                                                handler))))
            (testing "> status 500"
              (with-redefs [rems.api.services.command/command! (fn [_] (throw (Error. "BOOM")))]
                (is (response-is-server-error? (-> (request :post "/api/applications/submit")
                                                   (authenticate "42" "alice")
                                                   (json-body {:application-id 3})
                                                   handler)))))))
        (testing "> session"
          (let [cookie (login-with-cookies "malice")
                csrf (get-csrf-token cookie)]
            (testing "> GET"
              (is (response-is-ok? (-> (request :get "/api/catalogue")
                                       (header "Cookie" cookie)
                                       (header "x-csrf-token" csrf)
                                       handler))))
            (testing "> failed PUT"
              (is (response-is-forbidden? (-> (request :put "/api/catalogue-items/archived")
                                              (header "Cookie" cookie)
                                              (header "x-csrf-token" csrf)
                                              (json-body {:id 9999999 :archived true})
                                              handler))))))))
    (testing "user can't get log"
      (is (response-is-forbidden? (-> (request :get "/api/audit-log")
                                      (authenticate "42" "alice")
                                      handler))))
    (testing "reporter can get log"
      (is (= [{:userid nil :apikey nil :method "get" :path "/api/unknown" :status "404"}
              {:userid "owner" :apikey "42" :method "get" :path "/api/unknown" :status "404"}
              {:userid "alice" :apikey "42" :method "get" :path "/api/users/active" :status "403"}
              {:userid "owner" :apikey "42" :method "get" :path "/api/users/active" :status "200"}
              {:userid "alice" :apikey "42" :method "get" :path (str "/api/applications/" app-id) :status "200"}
              {:userid "reporter" :apikey "42" :method "get" :path (str "/api/applications/" app-id "/pdf") :status "200"}
              {:userid "alice" :apikey "43" :method "post" :path "/api/applications/submit" :status "200"}
              {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "400"}
              {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "500"}
              {:userid "malice" :apikey nil :method "get" :path "/api/catalogue" :status "200"}
              {:userid "malice" :apikey nil :method "put" :path "/api/catalogue-items/archived" :status "403"}
              {:userid "alice" :apikey "42" :method "get" :path "/api/audit-log" :status "403"}]
             (mapv #(dissoc % :time)
                   (-> (request :get "/api/audit-log")
                       (authenticate "42" "reporter")
                       handler
                       read-ok-body)))))
    (testing "filtering log by user"
      (is (= [{:userid "alice" :apikey "42" :method "get" :path "/api/users/active" :status "403"}
              {:userid "alice" :apikey "42" :method "get" :path (str "/api/applications/" app-id) :status "200"}
              {:userid "alice" :apikey "43" :method "post" :path "/api/applications/submit" :status "200"}
              {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "400"}
              {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "500"}
              {:userid "alice" :apikey "42" :method "get" :path "/api/audit-log" :status "403"}]
             (mapv #(dissoc % :time)
                   (-> (request :get "/api/audit-log?userid=alice")
                       (authenticate "42" "reporter")
                       handler
                       read-ok-body)))))
    (testing "filtering log by time"
      (is (= [{:userid nil :apikey nil :method "get" :path "/api/unknown" :status "404"}
              {:userid "owner" :apikey "42" :method "get" :path "/api/unknown" :status "404"}
              {:userid "alice" :apikey "42" :method "get" :path "/api/users/active" :status "403"}
              {:userid "owner" :apikey "42" :method "get" :path "/api/users/active" :status "200"}]
             (mapv #(dissoc % :time)
                   (-> (request :get (str "/api/audit-log?after=2000-01&before=" @time-a))
                       (authenticate "42" "reporter")
                       handler
                       read-ok-body))))
      (is (= []
             (-> (request :get "/api/audit-log?after=2100-01")
                 (authenticate "42" "reporter")
                 handler
                 read-ok-body))))
    (testing "filtering log by application"
      (is (= [{:userid "alice" :apikey "42" :method "get" :path (str "/api/applications/" app-id) :status "200"}
              {:userid "reporter" :apikey "42" :method "get" :path (str "/api/applications/" app-id "/pdf") :status "200"}]
             (mapv #(dissoc % :time)
                   (-> (request :get (str "/api/audit-log?application-id=" app-id))
                       (authenticate "42" "reporter")
                       handler
                       read-ok-body))))
      (is (= []
             (mapv #(dissoc % :time)
                   (-> (request :get "/api/audit-log?application-id=99999999")
                       (authenticate "42" "reporter")
                       handler
                       read-ok-body)))))))
