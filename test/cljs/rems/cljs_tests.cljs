(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            rems.administration.test-create-catalogue-item
            rems.administration.test-create-form
            rems.administration.test-create-license
            rems.administration.test-create-resource
            rems.administration.test-create-workflow
            rems.administration.test-items
            rems.common.application-util
            rems.common.form
            rems.common.util
            rems.common.test-util
            rems.flash-message
            rems.test-fields
            rems.test-table
            rems.test-util
            rems.text))

(doo-tests 'rems.administration.test-create-catalogue-item
           'rems.administration.test-create-form
           'rems.administration.test-create-license
           'rems.administration.test-create-resource
           'rems.administration.test-create-workflow
           'rems.administration.test-items
           'rems.common.application-util
           'rems.common.form
           'rems.common.util
           'rems.common.test-util
           'rems.flash-message
           'rems.test-fields
           'rems.test-table
           'rems.test-util
           'rems.text
           'rems.util)
