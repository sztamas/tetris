(ns tetris.async
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:refer-clojure :exclude [map filter])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! chan <! >! close!]]))

(defn- now [] (. (js/Date.) (getTime)))

(defn throttle [should-let-through? in-ch]
  (let [out-ch (chan)]
    (go
      (loop [last-put-at 0 throttled-since-last 0]
        (let [v (<! in-ch)
              now (now)]
          (if (nil? v)
            (close! out-ch)
            (if (should-let-through? last-put-at now throttled-since-last)
              (do
                (>! out-ch v)
                (recur now 0))
              (recur last-put-at (inc throttled-since-last)))))))
    out-ch))

(defn map [map-fn in-ch]
  (let [out-ch (chan)]
    (go (while true
      (if-let [v (<! in-ch)]
        (when-let [nv (map-fn v)]
          (>! out-ch nv))
        (close! out-ch))))
    out-ch))

(defn filter [filter-fn in-ch]
  (let [out-ch (chan)]
    (go (while true
      (if-let [value (<! in-ch)]
        (when (filter-fn value)
          (>! out-ch value))
      (close! out-ch))))
    out-ch))

(defn fan-in [in-chs]
  (let [out-ch (chan)]
    (go (while true
      (let [[value] (alts! in-chs)]
        (>! out-ch value))))
    out-ch))

(defn listen 
  ([el event-type]
   (listen el event-type (chan)))
  ([el event-type out-ch]
      (events/listen el event-type #(put! out-ch %))
      out-ch))

