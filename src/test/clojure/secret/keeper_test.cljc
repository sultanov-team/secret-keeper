(ns secret.keeper-test
  (:refer-clojure :exclude [read-string])
  (:require
    #?@(:clj
        [[clojure.edn :refer [read-string]]
         [clojure.test :refer [deftest testing is]]]
        :cljs
        [[cljs.reader :refer [read-string]]
         [cljs.test :refer [deftest testing is]]])
    [secret.keeper :as sut]))


(def parse
  (partial read-string {:readers {'secret sut/make-secret}}))


(deftest secret-keeper-test
  (testing "secrets should be parsed from the string"
    (let [expected {"#secret 123"                                    #secret{:data 123, :category :secret}
                    "#secret :keyword"                               #secret{:data :keyword, :category :secret}
                    "#secret \"string\""                             #secret{:data "string", :category :secret}
                    "#secret nil"                                    nil
                    "#secret TEST_TOKEN"                             #secret{:data "token_12345", :category :secret}
                    "#secret {:data \"string\", :category :private}" #secret{:data "string", :category :private}
                    "#secret {:data \"string\"}"                     #secret{:data "string", :category :secret}
                    "#secret {:data BAD_TOKEN}"                      nil
                    "#secret {:data TEST_TOKEN, :category :private}" #secret{:data "token_12345", :category :private}
                    "#secret {:data TEST_TOKEN}"                     #secret{:data "token_12345", :category :secret}
                    "#secret {:username \"john\"}"                   #secret{:data {:username "john"}, :category :secret}}]
      (doseq [[k v] expected]
        (let [actual (parse k)]
          (if actual
            (do
              (is (sut/secret? actual))
              (is (= (sut/data v) (sut/data actual)))
              (is (= (sut/category v) (sut/category actual)))
              (is (= v actual))
              (is (= (str v) (str actual)))
              (is (= (pr-str v) (pr-str actual))))
            (do
              (is (nil? actual))
              (is (false? (sut/secret? actual)))
              (is (nil? (sut/data actual)))
              (is (nil? (sut/category actual)))))))))

  (testing "nil and objects shouldn't be a secret"
    (let [coll [nil #?(:clj (Object.) :cljs (js/Object.))]]
      (doseq [actual coll]
        (is (false? (sut/secret? actual)))
        (is (nil? (sut/data actual)))
        (is (nil? (sut/category actual))))))

  (testing "make-secret function should be override the secret category"
    (let [expected (sut/make-secret 42 :confidential)
          actual1  (sut/make-secret expected)
          actual2  (sut/make-secret expected :private)]
      (is (= :confidential (sut/category actual1)))
      (is (= :private (sut/category actual2)))))

  (testing "make-secret function should be return a secret using the following data structures"
    (let [expected [42 "string" :keyword [1 2 3] #{1 2 3} {:a 1}]]
      (doseq [v expected]
        (let [actual1 (sut/make-secret v)
              actual2 (sut/make-secret v :private)]
          (is (every? sut/secret? [actual1 actual2]))
          (is (= v (sut/data actual1) (sut/data actual2)))
          (is (= sut/default-category (sut/category actual1)))
          (is (= :private (sut/category actual2))))))))
