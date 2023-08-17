(ns reveal.core
  (:require-macros [hiccups.core :refer [html]])
  (:require [clojure.string :refer [join]]
            [goog.dom :as gdom]
            [hiccups.runtime]
            [reveal.slides :as slides]))


;; When changing comments, you manually need to refresh your browser
(def options (clj->js {:hash true
                       :controls true
                       :controlsTutorial true
                       :progress false
                       :transition "fade"                   ; e.g. none/fade/slide/convex/concave/zoom
                       :slideNumber "c"
                       :plugins [js/RevealNotes js/RevealHighlight]}))


;; -----------------------------------------------------------------------------
;; You do not need to change anything below this comment

(defn convert
  "Get list of all slides and convert them to html strings."
  []
  (let [slides (slides/all)]
    (join (map #(html %) slides))))

(defn main
  "Get all slides, set them as innerHTML and reinitialize Reveal.js"
  []
  (set! (.. (gdom/getElement "slides") -innerHTML) (convert))
  (let [state (and (.isReady js/Reveal) (.getState js/Reveal))]
    (-> (.initialize js/Reveal options)
        (.then #(when state (.setState js/Reveal state)))
        (.then #(if (.isSpeakerNotes js/Reveal)
                  ;; disable figwheel connection for speaker notes
                  (when (.hasOwnProperty js/window "figwheel")
                    (set! (.-connect js/figwheel.repl) (constantly "Disabled for speaker notes")))

                  ;; trigger an event which will update the speaker notes
                  (.dispatchEvent js/Reveal (clj->js {:type "resumed"}))))
        (.then (fn [] "call your own init code from here")))))
(main)
