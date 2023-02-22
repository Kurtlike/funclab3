(ns lab3.core
  (:require [lab3.io :refer :all]
            [lab3.cubic-spline :refer :all]
            [lab3.linear :refer :all]
            [clojure.string :as str]
            [clojure.core.async
             :refer [chan close!]]))

(def train-points (ref []))
(def data-points (ref []))

(def control-rules {:train   #(str/starts-with? % "t")
                    :data    #(str/starts-with? % "d")
                    :resolve #(str/starts-with? % "resolve")})

(defn add-train-point [line]
  (let [split (vec (.split line ","))
        [_ x_s y_s & _] split
        x (Double/parseDouble x_s)
        y (Double/parseDouble y_s)]
    (println "new trust point: " x "," y)
    (-> train-points
        (alter conj [x y])
        (dosync))))

(defn add-data-point [line]
  (let [split (vec (.split line ","))
        [_ x_s & _] split
        x (Double/parseDouble x_s)]
    (println "new data point: " x)
    (-> data-points
        (alter conj x)
        (dosync))))

(defn predict-from-data [_]
  (let [sorted-train (sort-by first @train-points)
        data @data-points
        cubic-spline-resolved (future (mapv (spline-interpolator sorted-train) data))
        linear-resolved (future (mapv (linear-interpolator sorted-train) data))]
    (print "linear approximated: ")
    (doseq [i (range (count data))]
      (print " " (get data i) "," (get @linear-resolved i) "|"))
    (println)
    (print "spline approximated: ")
    (doseq [i (range (count data))]
      (print " " (get data i) "," (get @cubic-spline-resolved i) "|"))
    (println)))

(def main_chan (chan))

(defn shutdown-guard [effect]
  (let [shutdown-p (promise)
        hook-f (fn [] (do
                        (effect)
                        (println "Shutting down!!!")
                        (deliver shutdown-p 0)))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable hook-f))
    (System/exit @shutdown-p)))
(defn -main []
  (-> main_chan
      (input-reader)
      (manage-channels control-rules)
      (update :train #(updater % add-train-point))
      (update :data #(updater % add-data-point))
      (update :resolve #(updater % predict-from-data))
      (vals)
      (final-handler))
  (shutdown-guard #(close! main_chan)))
