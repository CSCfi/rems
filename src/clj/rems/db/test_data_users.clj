(ns rems.db.test-data-users
  (:require [clojure.test :refer :all]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.testing-util :refer [with-user]]))

(def +bot-users+
  {:approver-bot approver-bot/bot-userid
   :rejecter-bot rejecter-bot/bot-userid})

(def +bot-user-data+
  {approver-bot/bot-userid {:eppn approver-bot/bot-userid :commonName "Approver Bot"}
   rejecter-bot/bot-userid {:eppn rejecter-bot/bot-userid :commonName "Rejecter Bot"}})

(def +fake-users+
  {:applicant1 "alice"
   :applicant2 "malice"
   :approver1 "developer"
   :approver2 "handler"
   :organization-owner1 "organization-owner1"
   :organization-owner2 "organization-owner2"
   :owner "owner"
   :reporter "reporter"
   :reviewer "carl"
   :roleless1 "elsa"
   :roleless2 "frank"})

(def +fake-user-data+
  {"developer" {:eppn "developer" :mail "developer@example.com" :commonName "Developer" :nickname "The Dev"}
   "alice" {:eppn "alice" :mail "alice@example.com" :commonName "Alice Applicant" :organizations [{:organization/id "default"}] :nickname "In Wonderland"}
   "malice" {:eppn "malice" :mail "malice@example.com" :commonName "Malice Applicant" :twinOf "alice" :other "Attribute Value"}
   "handler" {:eppn "handler" :mail "handler@example.com" :commonName "Hannah Handler"}
   "carl" {:eppn "carl" :mail "carl@example.com" :commonName "Carl Reviewer"}
   "elsa" {:eppn "elsa" :mail "elsa@example.com" :commonName "Elsa Roleless"}
   "frank" {:eppn "frank" :mail "frank@example.com" :commonName "Frank Roleless" :organizations [{:organization/id "frank"}]}
   "organization-owner1" {:eppn "organization-owner1" :mail "organization-owner1@example.com" :commonName "Organization Owner 1" :organizations [{:organization/id "organization1"}]}
   "organization-owner2" {:eppn "organization-owner2" :mail "organization-owner2@example.com" :commonName "Organization Owner 2" :organizations [{:organization/id "organization2"}]}
   "owner" {:eppn "owner" :mail "owner@example.com" :commonName "Owner"}
   "reporter" {:eppn "reporter" :mail "reporter@example.com" :commonName "Reporter"}})

(def +demo-users+
  {:applicant1 "RDapplicant1@funet.fi"
   :applicant2 "RDapplicant2@funet.fi"
   :approver1 "RDapprover1@funet.fi"
   :approver2 "RDapprover2@funet.fi"
   :reviewer "RDreview@funet.fi"
   :organization-owner1 "RDorganizationowner1@funet.fi"
   :organization-owner2 "RDorganizationowner2@funet.fi"
   :owner "RDowner@funet.fi"
   :reporter "RDdomainreporter@funet.fi"})

(def +demo-user-data+
  {"RDapplicant1@funet.fi" {:eppn "RDapplicant1@funet.fi" :mail "RDapplicant1.test@test_example.org" :commonName "RDapplicant1 REMSDEMO1" :organizations [{:organization/id "default"}]}
   "RDapplicant2@funet.fi" {:eppn "RDapplicant2@funet.fi" :mail "RDapplicant2.test@test_example.org" :commonName "RDapplicant2 REMSDEMO"}
   "RDapprover1@funet.fi" {:eppn "RDapprover1@funet.fi" :mail "RDapprover1.test@rems_example.org" :commonName "RDapprover1 REMSDEMO"}
   "RDapprover2@funet.fi" {:eppn "RDapprover2@funet.fi" :mail "RDapprover2.test@rems_example.org" :commonName "RDapprover2 REMSDEMO"}
   "RDreview@funet.fi" {:eppn "RDreview@funet.fi" :mail "RDreview.test@rems_example.org" :commonName "RDreview REMSDEMO"}
   "RDowner@funet.fi" {:eppn "RDowner@funet.fi" :mail "RDowner.test@test_example.org" :commonName "RDowner REMSDEMO"}
   "RDorganizationowner1@funet.fi" {:eppn "RDorganizationowner1@funet.fi" :mail "RDorganizationowner1.test@test_example.org" :commonName "RDorganizationowner1 REMSDEMO" :organizations [{:organization/id "organization1"}]}
   "RDorganizationowner2@funet.fi" {:eppn "RDorganizationowner2@funet.fi" :mail "RDorganizationowner2.test@test_example.org" :commonName "RDorganizationowner2 REMSDEMO" :organizations [{:organization/id "organization2"}]}
   "RDdomainreporter@funet.fi" {:eppn "RDdomainreporter@funet.fi" :mail "RDdomainreporter.test@test_example.org" :commonName "RDdomainreporter REMSDEMO"}})

(def +oidc-users+
  {:applicant1 "WHFS36UEZD6TNURJ76WYLSVDCUUENOOF"
   :applicant2 "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI"
   :approver1 "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA"
   :approver2 "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ"
   :reviewer "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M"
   :reporter "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL"
   :organization-owner1 "W6OKPQGANG6QK54GRF7AOOGMZL7M6IVH"
   :organization-owner2 "D4ZJM7XNXKGFQABRQILDI6EYHLJRLYSF"
   :owner "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4"})

(def +oidc-user-data+
  {"WHFS36UEZD6TNURJ76WYLSVDCUUENOOF" {:eppn "WHFS36UEZD6TNURJ76WYLSVDCUUENOOF" :mail "RDapplicant1@mailinator.com" :commonName "RDapplicant1 REMSDEMO1" :organizations [{:organization/id "default"}]}
   "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI" {:eppn "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI" :mail "RDapplicant2@mailinator.com" :commonName "RDapplicant2 REMSDEMO"}
   "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA" {:eppn "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA" :mail "RDapprover1@mailinator.com" :commonName "RDapprover1 REMSDEMO"}
   "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ" {:eppn "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ" :mail "RDapprover2@mailinator.com" :commonName "RDapprover2 REMSDEMO"}
   "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M" {:eppn "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M" :mail "RDreview@mailinator.com" :commonName "RDreview REMSDEMO"}
   "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL" {:eppn "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL" :mail "RDdomainreporter@mailinator.com" :commonName "RDdomainreporter REMSDEMO"}
   "W6OKPQGANG6QK54GRF7AOOGMZL7M6IVH" {:eppn "W6OKPQGANG6QK54GRF7AOOGMZL7M6IVH" :mail "RDorganizationowner1@mailinator.com" :commonName "RDorganizationowner1 REMSDEMO" :organizations [{:organization/id "organization1"}]}
   "D4ZJM7XNXKGFQABRQILDI6EYHLJRLYSF" {:eppn "D4ZJM7XNXKGFQABRQILDI6EYHLJRLYSF" :mail "RDorganizationowner2@mailinator.com" :commonName "RDorganizationowner2 REMSDEMO" :organizations [{:organization/id "organization2"}]}
   "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4" {:eppn "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4" :mail "RDowner@mailinator.com" :commonName "RDowner REMSDEMO"}})

