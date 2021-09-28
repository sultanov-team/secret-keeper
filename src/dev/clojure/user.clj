(ns user
  "Development helper functions."
  (:require
    [hashp.core]
    [cljs.repl.node :as rn]
    [cider.piggieback :as pb]))


(defmacro jit
  "Just in time loading of dependencies."
  [sym]
  `(requiring-resolve '~sym))


(defn cljs-repl
  "Starts a ClojureScript REPL."
  []
  (pb/cljs-repl (rn/repl-env)))
