(ns secret.keeper
  "A Clojure(Script) library for keeping your secrets under control."
  (:refer-clojure :exclude [read])
  #?(:cljs
     (:require
       [cljs.reader :as reader]
       [goog.object :as gobj]
       [goog.string :as gstr]
       [goog.string.format]))
  #?(:clj
     (:import
       (clojure.lang
         PersistentArrayMap
         PersistentHashMap
         Symbol)
       (java.io
         Writer))))


(defn square
  "FIXME: remove me"
  [x]
  (* x x))


#?(:clj
   (set! *warn-on-reflection* true))



;;
;; Helpers
;;

(def default-category :secret)

(declare make-secret)


(defn- get-env
  "Returns the value of the environment variable using the specified key."
  ([key]
    (get-env key nil))
  ([key default]
    #?(:clj (or (System/getenv (str key)) default))
    #?(:cljs (or (gobj/get js/process.env key) default))))



;;
;; Protocols
;;

(defprotocol SecretReader
  "Secret reader protocol."
  :extend-via-metadata true
  (read [this] "Reads a secret."))


(defprotocol SecretBuilder
  "Secret builder protocol."
  :extend-via-metadata true
  (mark-as [data] [data category] "Makes a secret using the specified category.")
  (category [secret] "Returns the secret category.")
  (data [secret] "Returns the secret data."))



;;
;; Wrapper
;;

(defrecord Secret
  [data category]
  SecretBuilder
  (mark-as [secret] secret)
  (mark-as [_ new-category] (make-secret data new-category))
  (category [_] category)
  (data [_] data)

  Object
  (toString [_] (str {:data "*** CENSORED ***", :category category})))


(defn secret?
  "Checks if x is a Secret instance."
  [x]
  (#?(:clj instance?, :cljs implements?) Secret x))


(def tag
  #?(:clj  (.intern "#secret")
     :cljs "#secret"))


#?(:clj
   (defmethod print-method Secret [secret ^Writer writer]
     (.write writer (format "%s %s" tag secret))))


#?(:clj
   (defmethod print-dup Secret [secret ^Writer writer]
     (.write writer (format "%s %s" tag secret))))


;; FIXME: [2021-09-29, ilshat@sultanov.team] Doesn't work when printing in REPL (only cljs)

#?(:cljs
   (extend-type Secret
     IPrintWithWriter
     (-pr-writer [secret writer _opts]
       (-write writer (gstr/format "%s %s" tag secret)))))


#?(:cljs
   (reader/register-tag-parser! 'secret read))



;; Builders

(defn make-secret
  "Makes a secret using the specified category."
  ([data] (make-secret data default-category))
  ([data category] (->Secret data category)))


(extend-protocol SecretBuilder
  nil
  (mark-as
    ([_] nil)
    ([_ _] nil))
  (category [_] nil)
  (data [_] nil))


#?(:clj
   (extend-protocol SecretBuilder
     Object
     (mark-as
       ([data] (make-secret data))
       ([data category] (make-secret data category)))
     (category [_] nil)
     (data [_] nil))

   :cljs
   (extend-protocol SecretBuilder
     default
     (mark-as
       ([data] (make-secret data))
       ([data category] (make-secret data category)))
     (category [_] nil)
     (data [_] nil)))



;;
;; Readers
;;

(extend-protocol SecretReader
  nil
  (read [_] nil))


#?(:clj
   (extend-protocol SecretReader
     Object
     (read [data] (make-secret data))

     Symbol
     (read [key] (make-secret (get-env key)))

     PersistentArrayMap
     (read [map]
       (let [{:keys [category data default]
              :or   {category default-category
                     default  nil}} map]
         (cond
           (symbol? data) (make-secret (get-env data default) category)
           (some? data) (make-secret data category)
           :else (make-secret map))))

     PersistentHashMap
     (read [map]
       (let [{:keys [category data default]
              :or   {category default-category
                     default  nil}} map]
         (cond
           (symbol? data) (make-secret (get-env data default) category)
           (some? data) (make-secret data category)
           :else (make-secret map)))))

   :cljs
   (extend-protocol SecretReader
     default
     (read [data] (make-secret data))

     cljs.core/Symbol
     (read [key] (make-secret (get-env key)))

     cljs.core/PersistentArrayMap
     (read [map]
       (let [{:keys [category data default]
              :or   {category default-category
                     default  nil}} map]
         (cond
           (symbol? data) (make-secret (get-env data default) category)
           (some? data) (make-secret data category)
           :else (make-secret map))))

     cljs.core/PersistentHashMap
     (read [map]
       (let [{:keys [category data default]
              :or   {category default-category
                     default  nil}} map]
         (cond
           (symbol? data) (make-secret (get-env data default) category)
           (some? data) (make-secret data category)
           :else (make-secret map))))))



(comment
  (def f #?(:clj  (partial clojure.edn/read-string {:readers *data-readers*})
            :cljs reader/read-string))

  (def s (make-secret 123 :private))
  (category s) ;; => :private
  (data s) ;; => 123
  (str s) ;; => "{:data \"*** CENSORED ***\", :category :private}"
  (prn-str s) ;; => "#secret {:data \"*** CENSORED ***\", :category :private}\n"

  (reduce
    (fn [acc s]
      (assoc acc s (f s)))
    {} [
        "#secret #{1 2 3}"
        "#secret (1 2 3)"
        "#secret 123"
        "#secret :keyword"
        "#secret [1 2 3]"
        "#secret \"string\""
        "#secret \\a"
        "#secret nil"
        "#secret TEST_TOKEN"
        "#secret true"
        "#secret {:category :private :data \"string\"}"
        "#secret {:data \"string\"}"
        "#secret {:data BAD_TOKEN :default 5}"
        "#secret {:data TEST_TOKEN :default 5}"
        "#secret {:username \"john\"}"
        ])
  ;; =>
  ;; {
  ;;  "#secret #{1 2 3}"                              #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret (1 2 3)"                               #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret 123"                                   #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret :keyword"                              #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret [1 2 3]"                               #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret \"string\""                            #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret \\a"                                   #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret nil"                                   nil
  ;;  "#secret TEST_TOKEN"                            #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret true"                                  #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret {:category :private :data \"123\"}"    #secret{:data "*** CENSORED ***", :category :private}
  ;;  "#secret {:category :private :data \"string\"}" #secret{:data "*** CENSORED ***", :category :private}
  ;;  "#secret {:data \"string\"}"                    #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret {:data BAD_TOKEN :default 5}"          #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret {:data TEST_TOKEN :default 5}"         #secret{:data "*** CENSORED ***", :category :secret}
  ;;  "#secret {:username \"john\"}"                  #secret{:data "*** CENSORED ***", :category :secret}
  ;;  }
  )
