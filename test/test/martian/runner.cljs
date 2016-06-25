(ns martian.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [martian.test-test]))

(doo-tests 'martian.test-test)
