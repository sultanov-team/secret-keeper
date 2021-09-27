(ns run
  (:refer-clojure :exclude [test])
  (:require
    [clojure.pprint :as pprint]
    [clojure.set :as set]
    [clojure.tools.build.util.file :as file]
    [org.corfield.build :as bb]))


(defn- get-env
  [s]
  (System/getenv s))


(def defaults
  {:src-dirs        ["src/main/clojure"]
   :resource-dirs   ["src/main/resources"]
   :lib             'team.sultanov/secret-keeper
   :target          "target"
   :coverage-dir    "coverage"
   :jar-file        "target/secret-keeper.jar"
   :version         (get-env "lib-version")
   :build-number    (get-env "lib-build-number")
   :build-timestamp (get-env "lib-build-timestamp")
   :git-url         (get-env "lib-git-url")
   :git-branch      (get-env "lib-git-branch")
   :git-sha         (get-env "lib-git-sha")})


(defn- with-defaults
  [opts]
  (merge defaults opts))


(defn pretty-print
  [x]
  (binding [pprint/*print-right-margin* 130]
    (pprint/pprint x)))


(defn extract-meta
  [opts]
  (-> opts
    (select-keys [:lib
                  :version
                  :build-number
                  :build-timestamp
                  :git-url
                  :git-branch
                  :git-sha])
    (set/rename-keys {:lib :library})
    (update :library str)))


(defn write-meta
  [build-meta]
  (let [dir "src/main/resources/secret-keeper"]
    (file/ensure-dir dir)
    (->> build-meta
      (pretty-print)
      (with-out-str)
      (spit (str dir "/build.edn")))))


(defn repl
  [opts]
  (-> opts
    (with-defaults)
    (bb/run-task [:dev])))


(defn clean
  [opts]
  (-> opts
    (with-defaults)
    (bb/clean)))


(defn test-clj
  [opts]
  (-> opts
    (with-defaults)
    (bb/run-task [:test-clj])))


(defn test-cljs
  [opts]
  (-> opts
    (with-defaults)
    (bb/run-task [:test-cljs])))


(defn build
  [opts]
  (let [opts       (with-defaults opts)
        build-meta (extract-meta opts)]
    (write-meta build-meta)
    (bb/jar opts)))


(defn deploy
  [opts]
  (-> opts
    (with-defaults)
    (bb/deploy)))


(defn outdated
  [opts]
  (-> opts
    (with-defaults)
    (bb/run-task [:nop :antq])))
