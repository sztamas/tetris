Tetris
======

You can usually try the working latest version at: 

http://tamas-szabo.com/tetris/

Why?
----

Why does the world need another Tetris?

It doesn't, but it is just about time that I have my own Tetris!

OK, on a more serious note I've played around with Clojure in my free time for 1+ years now and I find the language very exciting, but haven't done any serious projects in it.

Recently, I've been looking at [core.async](https://github.com/clojure/core.async) and read most of David Nolen's blog posts on the subject at http://swannodette.github.io/.
I liked the ideas on building UIs in David's blog so I've decided to try it out. I needed a project that is preferably less trivial than a TODO application. 

I thought Tetris might be a good match.

Things tried, conclusions
-------------------------

First of all writing ClojureScript instead of JavaScript is of course a joy.

While developing I've used the [figwheel](https://github.com/bhauman/lein-figwheel) leiningen plugin that automatically compiles the files you change and pulls them into the browser.

The games state is stored in a ClojureScript atom. 
A watcher function does the rendering by drawing images for all the pieces (landed and falling) to a HTML5 Canvas. 
The moves, falling, and the rotation of the falling piece is done by a simple transformation of the piece. A general validator function set on the game atom ensures that the falling piece is on the board and doesn't overlap with any of the landed pieces.

Experiment with UIs built using `core.async` was my main goal for the project and I really like how the code handling events ended up.

For example this is the function that handles all the events: 

```clj
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

```

As you can see all the processing happens in one place. The code isn't scattered around callbacks. 

Also note that the event are high level ie. we don't handle left-arrow keydown we handle a higher level `:move-left` event.
This means that changing keybindings is trivial, but also that if we wanted to support a mouse (or gestures) all we have to do is make sure that a mouse listener will put a :move-left event in our event-channel. The rest of the code stays the same.

As another example the speed at which the falling piece falls is implemented by a ticker that puts `:tick` events on a channel at high speed. The `tick channel` is throttled then by a generic `channel throttler` that throttles based on the current level reached in the game.
