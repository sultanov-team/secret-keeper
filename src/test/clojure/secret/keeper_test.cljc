(ns secret.keeper-test
  #?(:clj
     (:require
       [clojure.edn :refer [read-string]]
       [clojure.test :refer [deftest testing is]]
       [secret.keeper :as sut])
     :cljs
     (:require
       [cljs.reader :refer [read-string]]
       [cljs.test :refer [deftest testing is]]
       [secret.keeper :as sut])))


(def parse
  (partial read-string {:readers {'secret sut/read}}))


(deftest parser-test
  (testing "secrets should be parsed from the string"
    (let [expected {"#secret 123"                                   #secret{:data 123, :category :secret}
                    "#secret :keyword"                              #secret{:data :keyword, :category :secret}
                    "#secret \"string\""                            #secret{:data "string", :category :secret}
                    "#secret TEST_TOKEN"                            #secret{:data "token_12345", :category :secret}
                    "#secret {:category :private :data \"string\"}" #secret{:data "string", :category :private}
                    "#secret {:data \"string\"}"                    #secret{:data "string", :category :secret}
                    "#secret {:data BAD_TOKEN :default 5}"          #secret{:data 5, :category :secret}
                    "#secret {:data TEST_TOKEN :default 5}"         #secret{:data "token_12345", :category :secret}
                    "#secret {:username \"john\"}"                  #secret{:data {:username "john"}, :category :secret}}]
      (doseq [[k v] expected]
        (let [actual (parse k)]
          ;; FIXME: [2021-09-29, ilshat@sultanov.team] Research this problem with cljs?
          #?(:clj (is (sut/secret? actual)))
          (is (= (sut/data v) (sut/data actual)))
          (is (= (sut/category v) (sut/category actual)))
          (is (= v actual))
          (is (= (str v) (str actual)))
          (is (= (pr-str v) (pr-str actual)))))))

  (testing "nil shouldn't be parsed from the string"
    (let [actual (parse "#secret nil")]
      (is (nil? actual))
      (is (false? (sut/secret? actual)))
      (is (nil? (sut/data actual)))
      (is (nil? (sut/category actual))))))
