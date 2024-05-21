(ns ^:dev/once rems.globals
  "Collection of global variables for REMS frontend.

  re-frame state is isolated and usually more complex, but can still make use
  of globals thanks to reagent."
  (:require [reagent.core :as r]))

(def ^:private state (r/atom {}))



;;; globals, these should use reagent.core/cursor

(def config "Map of configuration key/value pairs." (r/cursor state [::config]))

(def user "Map of current user's attributes." (r/cursor state [::identity :user]))

(def roles "Set of current user's roles." (r/cursor state [::identity :roles]))

(def language "Current user's preferred language." (r/cursor state [::language]))

(def theme "Map of theme key/value pairs." (r/cursor state [::theme]))

(def translations "Map of translation key/value pairs, in all available languages." (r/cursor state [::translations]))
