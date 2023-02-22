## Функциональное програмирование
#### Лабараторная работа №3
#### Рождественский Н.С., P34102
**Дисциплина:** "Функциональное программирование"

**Выполнил:** Рождественский Никита, P34102

**Вариант:**
Spline-interpolation

**Требования:**
1. должна быть реализована «аппроксимация» отрезками
2. настройки алгоритма аппроксимирования и выводимых данных должны задаваться через аргументы командной строки:
  * какой или какие алгоритмы использовать
  * частота дискретизации
  * и т.п.
3. входные данные должны задаваться в текстовом формате на подобии “.csv” (к примеру `x;y\n` или `x\ty\n` и т.п.) и поддаваться на стандартный ввод,
входные данные должны быть отсортированы по возрастанию x
4. выходные данные должны подаваться на стандартный вывод
5. программа должна работать в потоковом режиме (пример - cat | grep 11)

### Логика работы  

Программа постоянно считает поток входящих строчек с stdin, и сразу же отвечает на них в stdout.
Внутри работает все на async/chan.

Есть три сообщений которые программа обрабатывает:
1. `t,x,y` - добавление точки для выборки интерполятора
2. `d,x` - добавление точки для применения интерполятора
3. `resolve` - найти координату по нужному сплайну

#### Настройка Clojure-окружения
```
{:paths ["src"]
    :deps
        {org.clojure/clojure {:mvn/version "1.11.1"}
        org.clojure/tools.cli {:mvn/version "1.0.214"}
        org.clojure/test.check {:mvn/version "1.1.1"}
        org.clojure/core.async {:mvn/version "1.4.627"}}
:aliases{
	:test
 {:extra-paths ["test"]
  :extra-deps {com.cognitect/test-runner
               {:git/url "https://github.com/cognitect-labs/test-runner.git"
                :sha "209b64504cb3bd3b99ecfec7937b358a879f55c1"}
               org.clojure/test.check {:mvn/version "1.1.1"}}
  :main-opts ["-m" "cognitect.test-runner"]}
:run
  {:extra-paths ["src"]
  :main-opts ["-m" "lab3.core"]}
:lint
  {:extra-paths ["src" "test"]
  :extra-deps {cljfmt/cljfmt {:mvn/version "0.9.0"}}
  :main-opts ["-m" "cljfmt.main check"]}
:lint_fix
  {:extra-paths ["src" "test"]
  :extra-deps {cljfmt/cljfmt {:mvn/version "0.9.0"}}
  :main-opts ["-m" "cljfmt.main fix"]}
  }}
```

#### Ввод/вывод
```
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
```
#### Пример работы
```
kurtlike@debian:~/func/lab3$ cat input |  clj -Mrun | grep 5
new trust point:  -5.0 , 10.0
new trust point:  5.0 , 10.0
new data point:  -5.0
new data point:  5.0
linear approximated:   -6.0 , 12.0 |  -5.0 , 10.0 |  -2.0 , 6.0 |  2.0 , 6.0 |  5.0 , 10.0 |  6.0 , 12.0 |
spline approximated:   -6.0 , 12.136363636363637 |  -5.0 , 10.0 |  -2.0 , 4.636363636363637 |  2.0 , 4.636363636363638 |  5.0 , 10.0 |  6.0 , 12.136363636363637 |
```
