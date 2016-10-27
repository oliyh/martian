(ns dev
  (:require [cemerick.piggieback :as piggieback]
            [cljs.repl.nashorn :as nashorn]))

(defn cljs-repl []
  (piggieback/cljs-repl (nashorn/repl-env)))
