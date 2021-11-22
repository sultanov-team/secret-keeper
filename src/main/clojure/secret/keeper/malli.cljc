(ns secret.keeper.malli
  "The simple wrapper for the malli library.
  Easy markup your secrets in one place - directly in your model."
  (:require
    [malli.core :as m]
    [malli.transform :as mt]
    [secret.keeper :as keeper]))


(defn transformer
  "Secret transformer.

  - Encoder - encodes all secrets using the specified categories.
  - Decoder - decodes all secrets.

  Strategy:
    1. Try to extract the category from the schema properties using the specified key.
       Important: The local category has the highest priority.
    2. Try to extract the category from the transformer secrets map by the schema type
    3. Otherwise, if the schema has any entries, we take the specified categories
       from the transformer secrets map by the entry key

  Usage:
    ```
    ;; 1. Define your transformer
    (def Transformer
      (transformer
        {:key     :category  ;; by default ::keeper/category
         :secrets {:passport :confidential
                   :password :internal-only}}))

    ;; 2. Define your schema
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
                  [:country [:enum \"Russia\" \"USA\"]]]]
       [:credentials [:map
                      [:login string?]
                      [:password string?]]]])

    ;; 3. Mark all secrets using the specified categories
    (m/encode User <your-data> Transformer)

    ;; 4. Decode all secrets
    (m/decode User <your-data> Transformer)
    ```"
  ([]
   (transformer nil))
  ([{:keys [key secrets]
     :or   {key     ::keeper/category
            secrets {}}}]
   (let [global-keys  (set (keys secrets))
         get-category (fn [schema]
                        (or (some-> schema m/properties key)
                            (some->> schema m/type (get secrets))))

         encoder      (fn [schema _opts]
                        (if-some [category (get-category schema)]
                          (fn [x]
                            (keeper/make-secret x category))
                          (when (seq global-keys)
                            (when-some [entries (m/entries schema)]
                              (let [ks (reduce
                                         (fn [acc [k & _]]
                                           (if (contains? global-keys k)
                                             (assoc acc k (get secrets k))
                                             acc))
                                         {} entries)]
                                (fn [x]
                                  (if (map? x)
                                    (reduce-kv
                                      (fn [acc key category]
                                        (update acc key #(keeper/make-secret % category)))
                                      x ks)
                                    x)))))))

         decoder      (fn [_schema _opts]
                        (fn [x]
                          (if (keeper/secret? x)
                            (keeper/data x)
                            x)))]
     (mt/transformer
       {:default-encoder {:compile encoder}
        :default-decoder {:compile decoder}}))))
