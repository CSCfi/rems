(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            rems.administration.test-catalogue-item
            rems.administration.test-form
            rems.administration.test-items
            rems.administration.test-license
            rems.administration.test-resource
            rems.administration.test-workflow
            rems.test-application
            rems.test-util))

(doo-tests 'rems.administration.test-catalogue-item
           'rems.administration.test-form
           'rems.administration.test-items
           'rems.administration.test-license
           'rems.administration.test-resource
           'rems.administration.test-workflow
           'rems.test-application
           'rems.test-util)
