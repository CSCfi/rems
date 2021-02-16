(ns rems.schema-base
  "Fragments of schema shared between API, event and command schemas.

  Be careful when adding things here: we don't want to couple the API
  schema too tightly to internal schemas!"
  (:require [ring.swagger.json-schema :as rjs]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema User {:userid UserId})

(def FieldId s/Str)

(def FormId s/Int)

(s/defschema OrganizationId {:organization/id s/Str})

(s/defschema Language
  (rjs/field s/Keyword
             {:description "A language code"
              :example "en"}))

(s/defschema LocalizedString
  (rjs/field {Language s/Str}
             {:example {:fi "text in Finnish"
                        :en "text in English"}
              :description "Text values keyed by languages"}))

(s/defschema LocalizedInt
  (rjs/field {Language s/Int}
             {:example {:fi 1
                        :en 2}
              :description "Integers keyed by languages"}))

;; cond-pre generates a x-oneOf schema, which is
;; correct, but swagger-ui doesn't render it. We would need
;; to switch from Swagger 2.0 specs to OpenAPI 3 specs to get
;; swagger-ui support. However ring-swagger only supports
;; Swagger 2.0.
;;
;; As a workaround, add a manual description
(s/defschema FieldValue
  (rjs/field
   (s/cond-pre s/Str [[{:column s/Str :value s/Str}]])
   {:example "value"
    :description "A string for most fields, or [[{\"column\": string, \"value\": string}]] for table fields"}))

(s/defschema EventBase
  {(s/optional-key :event/id) s/Int
   :event/type s/Keyword
   :event/time DateTime
   :event/actor UserId
   :application/id s/Int})

(s/defschema UserWithAttributes
  {:userid UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organizations) [OrganizationId]
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :researcher-status-by) s/Str
   s/Keyword s/Any})

(s/defschema OrganizationOverview
  (merge OrganizationId
         {:organization/short-name LocalizedString
          :organization/name LocalizedString}))

(s/defschema OrganizationFull
  (merge OrganizationOverview
         {(s/optional-key :organization/modifier) UserWithAttributes
          (s/optional-key :organization/last-modified) DateTime
          (s/optional-key :organization/owners) [UserWithAttributes]
          (s/optional-key :organization/review-emails) [{:name LocalizedString
                                                         :email s/Str}]
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))
