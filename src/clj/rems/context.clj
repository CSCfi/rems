(ns rems.context
  "Collection of the global variables for REMS.

   When referring, please make your use greppable with the prefix context,
   i.e. context/*root-path*.")

(def ^:dynamic ^{:doc "Application root path also known as context-path.

  If application does not live at '/',
  then this is the path before application relative paths."} *root-path*)

;; TODO using api-formatted user data in context/*user* and elsewhere
;; would simplify things
(def ^:dynamic ^{:doc "User data available from request. These are raw user attributes (see rems.db.users)."} *user*)

(def ^:dynamic ^{:doc "Set of roles for user (or nil)"} *roles*)

(def ^:dynamic ^{:doc "Tempura object initialized with user's preferred language."} *tempura*)

(def ^:dynamic ^{:doc "User's preferred language."} *lang*)

(def ^:dynamic ^{:doc "Ongoing HTTP request if any."} *request*)
