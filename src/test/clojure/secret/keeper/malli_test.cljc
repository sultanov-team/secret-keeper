(ns secret.keeper.malli-test
  (:require
    #?(:clj  [clojure.test :refer [deftest testing is]]
       :cljs [cljs.test :refer [deftest testing is]])
    [malli.core :as m]
    [secret.keeper :as keeper]
    [secret.keeper.malli :as sut]))


(def Transformer
  (sut/transformer
    {:key     :category ; by default ::keeper/secret
     :secrets {:passport :confidential
               :password :internal-only}}))


(def User
  [:map
   [:firstname string?]
   [:lastname string?]
   [:email string?]
   [:passport string?]
   [:address [:map {:category :personal}
              [:street string?]
              [:zip int?]
              [:city string?]
              [:country [:enum "Russia" "USA"]]]]
   [:credentials [:map
                  [:login string?]
                  [:password string?]]]])


(def FakeUser
  {:firstname   "john"
   :lastname    "doe"
   :email       "john@doe.me"
   :passport    "123456789"
   :address     {:street  "1488 Sumner Street"
                 :zip     90025
                 :city    "West Los Angeles"
                 :country "USA"}
   :credentials {:login    "john"
                 :password "p4$$w0rd"}})


;; (m/encode User FakeUser Transformer)
;; =>
;; {:firstname   "john"
;;  :lastname    "doe"
;;  :email       "john@doe.me"
;;  :passport    #secret{:data     "*** CENSORED ***"
;;                       :category :confidential}
;;  :address     #secret{:data     "*** CENSORED ***"
;;                       :category :personal}
;;  :credentials {:login    "john"
;;                :password #secret{:data     "*** CENSORED ***"
;;                                  :category :internal-only}}}


(deftest transformer-test
  (testing "transformer should work without any options"
    (let [schema      [:map [:password string?]]
          transformer (sut/transformer)
          data        {:password "p4$$w0rd"}]
      (is (= data (as-> data $
                        (m/encode schema $ transformer)
                        (m/decode schema $ transformer))))))

  (testing "transformer should work with the specified options"
    (let [{:keys [passport address credentials]} (m/encode User FakeUser Transformer)
          password (:password credentials)]
      (is (every? keeper/secret? [passport address password]))
      (is (= :confidential (keeper/category passport)))
      (is (= (:passport FakeUser) (keeper/data passport)))
      (is (= (:address FakeUser) (keeper/data address)))
      (is (= :personal (keeper/category address)))
      (is (= (get-in FakeUser [:credentials :password]) (keeper/data password)))
      (is (= :internal-only (keeper/category password)))

      (is (= FakeUser (as-> FakeUser $
                            (m/encode User $ Transformer)
                            (m/decode User $ Transformer)))))))
