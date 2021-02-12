(ns rems.schema-base
  "Fragments of schema shared between API, event and command schemas.

  Be careful when adding things here: we don't want to couple the API
  schema too tightly to internal schemas!"
  (:require [ring.swagger.json-schema :as rjs]
            [schema.core :as s]))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema User {:userid UserId})

(def FieldId s/Str)

(def FormId s/Int)

;; TODO cond-pre generates a x-oneOf schema, which is
;; correct, but swagger-ui doesn't render it. We would need
;; to switch from Swagger 2.0 specs to OpenAPI 3 specs to get
;; swagger-ui support. However ring-swagger only supports
;; Swagger 2.0.
(s/defschema FieldValue
  (s/cond-pre s/Str [[{:column s/Str :value s/Str}]]))
