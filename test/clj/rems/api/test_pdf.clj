(ns ^:integration rems.api.test-pdf
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [luminus.http-server]
            [rems.db.testing :refer [reset-db-fixture test-data-fixture test-db-fixture]]
            [rems.api.testing :refer [handler-fixture]]
            [rems.json :as json]
            [rems.config]
            [rems.handler :refer [handler]]))

(use-fixtures
  :each
  test-db-fixture
  reset-db-fixture
  test-data-fixture
  handler-fixture)

(deftest test-pdf-smoke
    ;; need to spin up an actual http server so that something can serve
    ;; the headless chrome that generates the pdf
    (let [port 3093] ;; no way to automatically assign port with the ring jetty adapter
      (with-redefs [rems.config/env (assoc rems.config/env
                                           :public-url (str "http://localhost:" port "/"))]
        (let [server (luminus.http-server/start {:handler handler :port port})]
          (try
            (let [application-id (-> (http/get (str "http://localhost:" port "/api/applications")
                                               {:headers {"x-rems-api-key" "42"
                                                          "x-rems-user-id" "handler"}})
                                     :body
                                     json/parse-string
                                     first
                                     :application/id)
                  response (http/get (str "http://localhost:" port "/api/applications/" application-id "/pdf")
                                     {:throw-exceptions false
                                      :headers {"x-rems-api-key" "42"
                                                "x-rems-user-id" "reporter"}})]
              (is (= 200 (:status response)))
              (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
              (is (.startsWith (:body response) "%PDF-")))
            (finally
              (luminus.http-server/stop server)))))))
