(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            rems.test.administration.catalogue-item
            rems.test.administration.form
            rems.test.administration.items
            rems.test.administration.license
            rems.test.administration.resource
            rems.test.administration.workflow
            rems.test.application
            rems.test.util))

(doo-tests 'rems.test.administration.catalogue-item
           'rems.test.administration.form
           'rems.test.administration.items
           'rems.test.administration.license
           'rems.test.administration.resource
           'rems.test.administration.workflow
           'rems.test.application
           'rems.test.util)
