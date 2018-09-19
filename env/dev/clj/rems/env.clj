(ns rems.env
  (:require [rems.middleware.dev :refer [wrap-dev]]))

(def +defaults+ {:middleware wrap-dev})
