(ns rems.common.git
  #?(:clj (:require [rems.read-gitlog])
     :cljs (:require-macros [rems.read-gitlog])))

(def +repo-url+ "https://github.com/CSCfi/rems/")
(def +commits-url+ (str +repo-url+ "commits/"))
(def +tree-url+ (str +repo-url+ "tree/"))
(def +master-url+ (str +tree-url+ "master/"))
(def +version+ (rems.read-gitlog/read-current-version))
