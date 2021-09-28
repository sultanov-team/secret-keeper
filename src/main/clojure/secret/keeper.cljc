(ns secret.keeper
  (:refer-clojure :exclude [read])
  #?(:cljs
     (:require
       [goog.object :as gobj]))
  #?(:clj
     (:import
       (clojure.lang
         PersistentArrayMap
         PersistentHashMap
         Symbol))))


(defn square
  "FIXME: remove me"
  [x]
  (* x x))



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
  "Secret Reader protocol."
  :extend-via-metadata true
  (read [this] "Reads a secret."))


(defprotocol SecretBuilder
  "Secret builder protocol."
  :extend-via-metadata true
  (mark-as [data] [data category] "Makes a secret using the specified category."))



;;
;; Wrapper
;;

(defrecord Secret
  [data category]
  SecretBuilder
  (mark-as [secret] secret)
  (mark-as [_ new-category] (make-secret data new-category)))



;; Builders

(defn make-secret
  "Makes a secret using the specified category."
  ([data] (make-secret data default-category))
  ([data category] (->Secret data category)))


(extend-protocol SecretBuilder
  nil
  (mark-as
    ([_] nil)
    ([_ _] nil)))


#?(:clj
   (extend-protocol SecretBuilder
     Object
     (mark-as
       ([data] (make-secret data))
       ([data category] (make-secret data category))))

   :cljs
   (extend-protocol SecretBuilder
     default
     (mark-as
       ([data] (make-secret data))
       ([data category] (make-secret data category)))))



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
  (def f (partial clojure.edn/read-string {:readers *data-readers*}))
  (reduce
    (fn [acc s]
      (assoc acc s (f s)))
    {} ["#secret \"string\"" ;; => #secret.keeper.Secret{:data "string", :category :secret}
        "#secret 123" ;; => #secret.keeper.Secret{:data 123, :category :secret}
        "#secret true" ;; => #secret.keeper.Secret{:data true, :category :secret}
        "#secret \\a" ;; => #secret.keeper.Secret{:data \a, :category :secret}
        "#secret :keyword" ;; => #secret.keeper.Secret{:data :keyword, :category :secret}
        "#secret TEST_TOKEN" ;; => #secret.keeper.Secret{:data "token_12345", :category :secret}
        "#secret {:category :private :data \"123\"}" ;; => #secret.keeper.Secret{:data "123", :category :private}
        "#secret {:username \"john\"}" ;; => #secret.keeper.Secret{:data {:username "john"}, :category :secret}
        "#secret {:data BAD_TOKEN :default 5}" ;; => default => #secret.keeper.Secret{:data 5, :category :secret}
        "#secret {:data TEST_TOKEN :default 5}" ;; => #secret.keeper.Secret{:data "token_12345", :category :secret}
        "#secret {:data \"string\"}" ;; => #secret.keeper.Secret{:data "string", :category :secret}
        "#secret {:category :private :data \"string\"}" ;; => #secret.keeper.Secret{:data "string", :category :private}
        "#secret [1 2 3]" ;; => #secret.keeper.Secret{:data [1 2 3], :category :secret}
        "#secret (1 2 3)" ;; => #secret.keeper.Secret{:data (1 2 3), :category :secret}
        "#secret #{1 2 3}" ;; => #secret.keeper.Secret{:data #{1 3 2}, :category :secret}
        "#secret nil" ;; => nil
        ])
  )
