(defproject tetris "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [figwheel "0.3.7"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  
  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.7"]]

  :cljsbuild {
    :builds [{:source-paths ["src"]
              :compiler {
                :output-to "resources/public/js/compiled/tetris.js"
                :output-dir "resources/public/js/compiled/out"
                :optimizations :none}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                :output-to "resources/public/js/tetris_prod.js"
                :output-dir "resources/public/js/compiled/prod-out"
                :optimizations :advanced
                :pretty-print false}}
             ]}
  :figwheel {
             :http-server-root "public" ;; default and assumes "resources" 
             :server-port 3449 ;; default
             :css-dirs ["public/resources/css"] ;; watch and update CSS
             })
