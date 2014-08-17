(ns tetris.canvas)

(def DEFAULT-FONT "bold 40px Courier")

(declare ^:dynamic *canvas*)
(defn context [] (.getContext *canvas* "2d"))

(defn width [] (.-width *canvas*))
(defn height [] (.-height *canvas*))

(defn clear []
  (.clearRect (context) 0 0 (width) (height)))

(defn fill-rect [x1 y1 x2 y2 color]
  (let [ctx (context)
        saved-fill-style (.-fillStyle ctx)]
    (.beginPath ctx)
    (.rect ctx x1 y1 x2 y2)
    (set! (.-fillStyle ctx) color)
    (.fill ctx)
    (set! (.-fillStyle ctx) saved-fill-style)))

(defn draw-image [image x y w h]
  (.drawImage (context) image x y w h))

(defn measure-text [text]
  (let [ctx (context)]
    (set! (.-font ctx) DEFAULT-FONT)
    {:width (.-width (.measureText ctx text)) :height 32}))

(defn display-text [text x y & {:keys [color]}]
  (let [ctx (context)]
    (set! (.-fillStyle ctx) color)
    (set! (.-textAlign ctx) "center")
    (set! (.-textBaseline ctx) "middle")
    (set! (.-font ctx) DEFAULT-FONT)
    (.fillText (context) text x y)))
