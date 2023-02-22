(ns lab3.io
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go-loop chan buffer close! thread
                     alts! alts!! timeout]]))

(defn input-reader [inp-chan]
  (go-loop [counter 0]
    (if-let [next-line (read-line)]
      (if-let [_ (>! inp-chan next-line)]
        (recur (inc counter))
        (println "channel closed"))))
  inp-chan)

(defn manage-channels [inp-chan rules]
  (let [cs (into (hash-map) (mapv (fn [[key _]] [key (chan)]) rules))
        vector_rt (vec rules)]
    (go-loop []
      (if-let [next-val (<! inp-chan)]
        (do
          (let [channels (->> vector_rt
                              (filter #(apply (last %) [next-val]))
                              (mapv first)
                              (mapv #(get cs %)))]
            (if channels
              (doseq [c channels]
                (>! c next-val))))
          (recur))
        (doseq [[_ channel] cs]
          (println "closing channels")
          (close! channel))))
    cs))

(defn final-handler [inp-cs]
  (go-loop []
    (if-let [_ (alts! inp-cs)]
      (recur)
      (println "out channel closed"))))

(defn updater [inp-chan func]
  (let [opt-chan (chan)]
    (go-loop []
      (if-let [next-val (<! inp-chan)]
        (do
          (func next-val)
          (>! opt-chan next-val)
          (recur))
        (close! opt-chan))) opt-chan))