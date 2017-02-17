(ns rems.context
  "Collection of the global variables for REMS.

   When referring, please make your use greppable with the prefix context,
   i.e. context/*root-path*.")

(def ^:dynamic *root-path* nil)
