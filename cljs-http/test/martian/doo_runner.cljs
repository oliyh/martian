(ns martian.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [martian.cljs-http-test]))

(doo-tests 'martian.cljs-http-test)
