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
