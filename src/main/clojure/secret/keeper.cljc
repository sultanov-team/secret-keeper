(ns secret.keeper
  "A Clojure(Script) library for keeping your secrets under control."
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


#?(:clj
   (set! *warn-on-reflection* true))



;;
;; Helpers
;;

(defn get-env
  "Returns the value of the environment variable using the specified key."
  ([key]
    (get-env key nil))
  ([key default]
    #?(:clj (or (System/getenv (str key)) default))
    #?(:cljs (or (gobj/get js/process.env key) default))))



;;
;; Protocols
;;

(defprotocol ISecretBuilder
  "Secret builder protocol."
  :extend-via-metadata true
  (make-secret [data] [data category] "Makes a secret using the specified category."))


(defprotocol ISecret
  "Secret protocol."
  :extend-via-metadata true
  (secret? [x] "Returns `true` if `x` is a secret.")
  (data [secret] "Returns the secret data.")
  (category [secret] "Returns the secret category."))



;;
;; Wrapper
;;

(def default-category :secret)
(declare ->Secret)


(defrecord Secret
  [data category]
  ISecret
  (secret? [_] true)
  (data [_] data)
  (category [_] category)

  ISecretBuilder
  (make-secret [secret] secret)
  (make-secret [_ new-category] (->Secret data new-category))

  Object
  (toString [_] (str {:data "*** CENSORED ***", :category category})))



;;
;; Printers
;;

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
   (reader/register-tag-parser! 'secret make-secret))



;;
;; Builders
;;

(extend-protocol ISecretBuilder
  nil
  (make-secret
    ([_] nil)
    ([_ _] nil)))


#?(:clj
   (extend-protocol ISecretBuilder
     Object
     (make-secret
       ([data] (->Secret data default-category))
       ([data category] (->Secret data category)))

     Symbol
     (make-secret
       ;; get-env can be nil
       ([key] (make-secret (get-env key)))
       ([key category] (make-secret (get-env key) category)))

     PersistentArrayMap
     (make-secret
       ([map]
         (let [{:keys [category data]
                :or   {category default-category}} map]
           (if data
             (make-secret data category)
             (->Secret map category))))
       ([map category] (->Secret map category)))

     PersistentHashMap
     (make-secret
       ([map]
         (let [{:keys [category data]
                :or   {category default-category}} map]
           (if data
             (make-secret data category)
             (->Secret map category))))
       ([map category] (->Secret map category))))

   :cljs
   (extend-protocol ISecretBuilder
     default
     (make-secret
       ([data] (->Secret data default-category))
       ([data category] (->Secret data category)))

     cljs.core/Symbol
     (make-secret
       ;; get-env can be nil
       ([key] (make-secret (get-env key)))
       ([key category] (make-secret (get-env key) category)))

     cljs.core/PersistentArrayMap
     (make-secret
       ([map]
         (let [{:keys [category data]
                :or   {category default-category}} map]
           (if data
             (make-secret data category)
             (->Secret map category))))
       ([map category] (->Secret map category)))

     cljs.core/PersistentHashMap
     (make-secret
       ([map]
         (let [{:keys [category data]
                :or   {category default-category}} map]
           (if data
             (make-secret data category)
             (->Secret map category))))
       ([map category] (->Secret map category)))))



;;
;; Secrets
;;

(extend-protocol ISecret
  nil
  (secret? [_] false)
  (data [_] nil)
  (category [_] nil))


#?(:clj
   (extend-protocol ISecret
     Object
     (secret? [_] false)
     (data [_] nil)
     (category [_] nil))

   :cljs
   (extend-protocol ISecret
     default
     (secret? [_] false)
     (data [_] nil)
     (category [_] nil)))
