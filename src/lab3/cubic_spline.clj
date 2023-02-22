(ns lab3.cubic-spline)

(defn cubic-func [x-i a b c d]
  (fn [x] (let [diff (- x x-i)]
            (+ a (* b diff) (* c diff diff) (* d diff diff diff)))))

(defn cubic-spline [splines]
  (fn [x] (cond
            (< x (-> splines first first)) (apply (-> splines first last) [x])
            (> x (-> splines last (get 1))) (apply (-> splines last last) [x])
            :else (apply (->> splines
                              (filter #(let [[x-prev x-next _] %] (and (<= x-prev x) (<= x x-next))))
                              first
                              last) [x]))))

(defn spline-interpolator
  [points]
  (if (< (count points) 3)
    (fn [_] "Not enough data")
    (let [n (dec (count points))
          xs (mapv first points)
          ys (mapv last points)
          hs (mapv #(- (get xs (inc %)) (get xs %)) (range n))
          alphas (mapv #(-
                         (/ (* 3.0 (- (get ys (inc %)) (get ys %))) (get hs %))
                         (/ (* 3.0 (- (get ys %) (get ys (dec %)))) (get hs (dec %)))) (range 1 n))

          l! (transient (vec (repeat (inc n) 1.0)))
          z! (transient (vec (repeat (inc n) 0.0)))
          mu! (transient (vec (repeat n 0.0)))]
      (dorun (map #(do
                     (assoc! l! % (-
                                   (* 2.0 (- (get xs (inc %)) (get xs (dec %))))
                                   (* (get hs (dec %)) (get mu! (dec %)))))
                     (assoc! mu! % (/ (get hs %) (get l! %)))
                     (assoc! z! % (/ (-
                                      (get alphas (dec %))
                                      (* (get hs (dec %)) (get z! (dec %))))
                                     (get l! %)))) (range 1 n)))
      (let [z (persistent! z!) mu (persistent! mu!)
            as ys
            bs! (transient (vec (repeat n 0.0)))
            cs! (transient (vec (repeat (inc n) 0.0)))
            ds! (transient (vec (repeat n 0.0)))]
        (dorun (map #(do
                       (assoc! cs! % (- (get z %) (* (get mu %) (get cs! (inc %)))))
                       (assoc! bs! % (-
                                      (/ (- (get as (inc %)) (get as %)) (get hs %))
                                      (*
                                       (get hs %)
                                       (+ (get cs! (inc %)) (* 2.0 (get cs! %)))
                                       (/ 1.0 3.0))))
                       (assoc! ds! % (/
                                      (- (get cs! (inc %)) (get cs! %))
                                      (* 3.0 (get hs %))))) (reverse (range n))))
        (let [bs (persistent! bs!) cs (persistent! cs!) ds (persistent! ds!)]
          (cubic-spline (->> (range (dec (count xs)))
                             (map #(vector (get xs %)
                                           (get xs (inc %))
                                           (cubic-func
                                            (get xs %)
                                            (get as %)
                                            (get bs %)
                                            (get cs %)
                                            (get ds %))))
                             (sort-by first))))))))