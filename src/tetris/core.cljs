(ns tetris.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as dom]
            [cljs.core.async :refer [chan <! >! timeout]]
            [clojure.set :refer [intersection]]
            [tetris.async :as a]
            [tetris.canvas :as canvas]))

; ---------- Definitions ----------

(def COLS 10)
(def ROWS 20)
(defn block-size [] (quot (canvas/height) ROWS))

(def TICK-INTERVAL 10)
(def MAX-FALL-INTERVAL 300)
(def MAX-LEVEL 10)
(def SWITCH-LEVEL-EACH 200)

(def PIECE-ENTRY-ROW 4)

(def LEFT-ARROW 37)
(def RIGHT-ARROW 39)
(def DOWN-ARROW 40)
(def UP-ARROW 38)
(def P-KEY 80)
(def N-KEY 78)

(def PIECE-SHAPES {
  :i [[0 0] [0 1] [0 2] [0 3]]
  :o [[0 0] [1 0] [1 1] [0 1]]
  :t [[0 1] [1 0] [1 1] [2 1]]
  :j [[0 0] [1 0] [1 1] [1 2]]
  :l [[0 0] [0 1] [0 2] [1 0]]
  :s [[0 0] [1 0] [1 1] [2 1]]
  :z [[0 1] [1 0] [1 1] [2 0]]
})

(def PIECE-TYPES (keys PIECE-SHAPES))

(defn- create-image [url]
  (let [img (js/Image.)]
    (set! (.-src img) url)
    img))

(defn- piece-type->img [piece-type img-prefix]
  (let [img-path (str "img/" img-prefix "_" (name piece-type) ".png")]
    [piece-type (create-image img-path)]))

(defn- load-images [img-prefix]
  (into {} (map #(piece-type->img % img-prefix) PIECE-TYPES)))

(def BLOCK-IMAGES (load-images "block"))
(def PIECE-IMAGES (load-images "piece"))

(def BGCOLOR "black")

; ---------- Pieces and Blocks ----------

(defn create-piece [piece-type col row]
  {:type piece-type,
   :position {:col col :row row},
   :blocks (piece-type PIECE-SHAPES)})

(defn- block-row [b] (second b))
(defn- block-col [b] (first b))

(defn piece-blocks [{{:keys [col row]} :position, blocks :blocks :as p}]
  (map (fn [[bcol brow]] [(+ col bcol) (+ row brow)]) blocks))

(defn no-blocks-left? [piece] (empty? (:blocks piece)))

(defn move-piece [piece dir]
  (condp = dir
    :down (update-in piece [:position :row] dec)
    :left (update-in piece [:position :col] dec)
    :right (update-in piece [:position :col] inc)))

(defn rotate-block [[col row] max-col]
  [row (- max-col col)])

(defn rotate-piece [piece]
  (let [blocks (:blocks piece)
        max-col (apply max (map first blocks))
        new-blocks (map #(rotate-block % max-col) blocks)]
    (assoc piece :blocks new-blocks)))

(defn create-random-piece []
  (let [pt (rand-nth PIECE-TYPES)]
    (create-piece pt PIECE-ENTRY-ROW (inc ROWS))))

(defn normalise-piece [piece]
  (let [rows (map block-row (:blocks piece))
        min-row (apply min rows)]
    (-> piece
        (update-in [:position :row] #(+ % min-row))
        (update-in [:blocks] (fn [blocks] (map (fn [[col row]] [col (- row min-row)]) blocks))))))

; ---------- Game functions ----------

; "Definition" of a game
(def starting-state {
  :game-over false
  :running true
  :score nil
  :level nil
  :falling-piece nil
  :next-piece nil
  :pieces []})

(defn game-over? [game-state] (:game-over game-state))
(defn game-running? [game-state] (:running game-state))

(defn game-over! [game]
  (swap! game merge {:running false, :game-over true}))

(defn toggle-pause-game! [game]
  (swap! game update-in [:running] not))

(defn calculate-score [current-score rows-cleared]
  (+ current-score (* 10 rows-cleared) (if (= rows-cleared 4) 20)))

(defn update-score! [game rows-cleared]
  (swap! game update-in [:score] #(calculate-score % rows-cleared)))

(defn score->level [score]
  (min MAX-LEVEL (inc (quot score SWITCH-LEVEL-EACH))))

(defn update-level! [game]
  (swap! game #(assoc % :level (score->level (:score %)))))

(defn landed-blocks [game-state]
  (apply hash-set (mapcat piece-blocks (:pieces game-state))))

(defn all-pieces [game-state]
  (conj (:pieces game-state) (:falling-piece game-state)))

(defn piece-on-board? [piece]
  (every? (fn [[col row]] (and (<= 1 col COLS) (<= 1 row))) (piece-blocks piece)))

(defn piece-overlap? [game-state piece]
  (let [landed-blocks (landed-blocks game-state)
        piece-blocks (apply hash-set (piece-blocks piece))]
      (not (empty? (intersection landed-blocks piece-blocks)))))

(defn falling-piece-valid? [{:keys [falling-piece] :as game-state}]
  (and (piece-on-board? falling-piece)
       (not (piece-overlap? game-state falling-piece))))

(defn move-falling-piece! [game dir]
  (try
    (swap! game update-in [:falling-piece] #(move-piece % dir))
  (catch js/Error e)))

(defn make-falling-piece-fall! [game] (move-falling-piece! game :down))

(defn rotate-falling-piece! [game]
  (try
    (swap! game update-in [:falling-piece] rotate-piece)
  (catch js/Error e)))

(defn entered-board? [piece]
  (every? (fn [[col row]] (<= row ROWS)) (piece-blocks piece)))

(defn- map-values [f d]
  (zipmap (keys d) (map f (vals d))))

(defn falling-piece->landed-piece! [game]
  (swap! game (fn [g]
    (let [falling-piece (:falling-piece g)]
      (-> g
          (dissoc :falling-piece)
          (update-in [:pieces] #(conj % falling-piece)))))))

(defn add-next-falling-piece! [game]
  (swap! game (fn [g]
                (let [next-piece (or (:next-piece g) (create-random-piece))]
                  (-> g
                      (assoc :falling-piece next-piece)
                      (assoc :next-piece (create-random-piece)))))))

(defn rows-below [row rows]
  (count (filter #(< % row) rows)))

(defn drop-rows [{{:keys [row]} :position :as p} rows-to-drop]
  (update-in p [:blocks] (fn [blocks]
                            (map (fn [[bcol brow]] [bcol (- brow (rows-below (+ row brow) rows-to-drop))]) blocks))))

(defn completed-rows [pieces]
  (let [blocks (mapcat piece-blocks pieces)
        blocks-by-row (group-by block-row blocks)]
    (->> (map-values count blocks-by-row) ; row no -> block count
         (filter #(= COLS (second %))) ; has a block for each column
         (map first)))) ; get the row number

(defn cut-rows-from-piece [{{:keys [row]} :position :as piece} rows-to-remove]
  (let [remove? (apply hash-set rows-to-remove)]
    (update-in piece [:blocks] (fn [blocks] (remove #(remove? (+ row (block-row %))) blocks)))))

(defn clear-rows-from-pieces [pieces]
  (if-let [completed-rows (completed-rows pieces)]
    (->> pieces
         (map #(cut-rows-from-piece % completed-rows))
         (map #(drop-rows % completed-rows))
         (remove no-blocks-left?)
         (map normalise-piece))))

(defn clear-rows! [game]
  (let [completed-rows (completed-rows (:pieces @game))]
    (when (not (empty? completed-rows))
      (swap! game update-in [:pieces] clear-rows-from-pieces))
    (count completed-rows)))

; ---------- Rendering ----------

(defn show-message [text color]
  (let [middle-x (quot (canvas/width) 2)
        middle-y (quot (canvas/height) 2)
        {:keys [width height]} (canvas/measure-text text)]
    (canvas/fill-rect (- middle-x (quot width 2)) (- middle-y (quot height 2)) width height BGCOLOR)
    (canvas/display-text text middle-x middle-y :color color)) )

(defn- block-image-placement [[col row]]
  (let [block-size (block-size)
        x (* (dec col) block-size)
        y (* (- ROWS row) block-size)]
    [x y block-size block-size]))

(defn render-block [piece-type block]
  (let [image (piece-type BLOCK-IMAGES)
        placement (block-image-placement block)]
     (apply canvas/draw-image image placement)))

(defn render-pieces [pieces]
  (let [pieces-by-type (group-by :type pieces)
        blocks-by-type (map-values #(mapcat piece-blocks %) pieces-by-type)]
  (doseq [[t blocks] blocks-by-type]
    (doseq [b blocks]
      (render-block t b)))))

(defn render-score [score]
  (let [score-el (.getElementById js/document "score-value")]
    (dom/setTextContent score-el (str score))))

(defn render-level [level]
  (let [score-el (.getElementById js/document "level-value")]
    (dom/setTextContent score-el (str level))))

(defn render-next-piece [{piece-type :type :as p}]
  (let [score-el (.getElementById js/document "next-img-container")]
    (dom/removeChildren score-el)
    (dom/appendChild score-el (piece-type PIECE-IMAGES))))

(defn render [old-state game-state]
  (binding [canvas/*canvas* (.getElementById js/document "tetris-board")]
    (canvas/fill-rect 0 0 (canvas/width) (canvas/height) BGCOLOR)
    (render-pieces (all-pieces game-state))
    (render-score (:score game-state))
    (render-level (:level game-state))
    (when-let [next-piece (:next-piece game-state)]
      (render-next-piece next-piece))
    (if (game-over? game-state)
      (show-message "Game Over!" "red")
      (when-not (game-running? game-state)
        (show-message "PAUSED" "green")))))

; ---------- Events ----------

(defn tick-channel [game]
  (let [ch (chan)]
    (go
      (while true
        (<! (timeout TICK-INTERVAL))
        (>! ch :tick)))
    ch))

(defn keycode->event [key-event]
  (condp = (.-keyCode key-event)
   UP-ARROW :rotate
   DOWN-ARROW :fall
   LEFT-ARROW :move-left
   RIGHT-ARROW :move-right
   P-KEY :toggle-pause
   N-KEY :new-game
   nil
  ))

(defn key-events [] (a/listen js/document "keydown"))

(defn fall-interval [level]
  (let [dec-per-level (quot MAX-FALL-INTERVAL MAX-LEVEL)
        interval (- MAX-FALL-INTERVAL (* dec-per-level (dec level)))]
    (max TICK-INTERVAL interval)))

(defn should-fall? [game last-fall-at now _]
  (>= now (+ last-fall-at (fall-interval (:level @game)))))

(defn create-event-channel [game]
  (let [fall-channel (->> (tick-channel game)
                          (a/throttle (partial should-fall? game))
                          (a/map (constantly :fall)))
        key-channel (->> (key-events) (a/map keycode->event))]
    (a/filter
      #(when (or (game-running? @game) (#{:toggle-pause :new-game} %)) %)
      (a/fan-in [fall-channel key-channel]))))

; ---------- End of Events ----------

(defn start-new-game! [game]
  (reset! game starting-state)
  (swap! game merge {:level 1, :score 0})
  (add-next-falling-piece! game))

(defonce game (atom starting-state))
(set-validator! game falling-piece-valid?)
(add-watch game :changed (fn [_ _ old-state new-state] (render old-state new-state)))

(defn process-events [game event-channel]
    (go (while true
      (let [event (<! event-channel)]
        (condp = event
          :fall
            (let [landed? (not (make-falling-piece-fall! game))]
              (when landed?
                (if-not (entered-board? (:falling-piece @game))
                  (game-over! game)
                  (do
                    (falling-piece->landed-piece! game)
                    (let [rows-cleared (clear-rows! game)]
                      (when (not= 0 rows-cleared)
                        (update-score! game rows-cleared)
                        (update-level! game)))
                    (add-next-falling-piece! game)))))
          :move-left
            (move-falling-piece! game :left)
          :move-right
            (move-falling-piece! game :right)
          :rotate
            (rotate-falling-piece! game)
          :new-game
            (start-new-game! game)
          :toggle-pause
            (toggle-pause-game! game))))))

(defn init [game]
  (reset! game starting-state)
  (let [event-channel (create-event-channel game)]
    (process-events game event-channel)))

(init game)
