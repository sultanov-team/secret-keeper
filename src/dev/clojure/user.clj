(ns user
  "Development helper functions."
  (:require
    [hashp.core]))


(defmacro jit
  "Just in time loading of dependencies."
  [sym]
  `(requiring-resolve '~sym))

