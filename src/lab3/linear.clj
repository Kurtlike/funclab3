(ns lab3.linear)

(defn linear-func [a b]
  (fn [x] (+ a (* b x))))

(defn linear-int [segments]
  (fn [x] (cond
            (< x (-> segments first first)) (apply (-> segments first last) [x])
            (> x (-> segments last (get 1))) (apply (-> segments last last) [x])
            :else (apply (->> segments
                              (filter #(let [[x-prev x-next _] %] (and (<= x-prev x) (<= x x-next))))
                              first
                              last) [x]))))

(defn linear-interpolator
  [points]
  (if (< (count points) 2)
    (fn [_] "Not enough data")
    (let [n (dec (count points))
          xs (mapv first points)
          ys (mapv last points)
          as! (transient (vec (repeat n 0.0)))
          bs! (transient (vec (repeat n 0.0)))]
      (dorun (map #(do
                     (assoc! bs! % (/ (- (get ys %) (get ys (inc %))) (- (get xs %) (get xs (inc %)))))
                     (assoc! as! % (- (get ys %) (* (get bs! %) (get xs %)))))
                  (range n)))
      (let [as (persistent! as!) bs (persistent! bs!)]
        (linear-int (->> (range (dec (count xs)))
                         (map #(vector (get xs %)
                                       (get xs (inc %))
                                       (linear-func
                                        (get as %)
                                        (get bs %))))
                         (sort-by first)))))))