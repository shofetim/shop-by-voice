(ns shop.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.button :as b]
            [om-bootstrap.nav :as n]
            [om-bootstrap.input :as i]
            [om-bootstrap.random :as r]
            [cljs.core.async :refer [close! put! take! chan <! >!]]
            [cljs-http.client :as http]))

(enable-console-print!)

;; ──────────────────────────────────────────────────────────────────────
;; Utils


(defn speak [msg]
  (.speak js/speechSynthesis (new js/SpeechSynthesisUtterance msg)))

;; ──────────────────────────────────────────────────────────────────────
;; Wit.ai

(defn info [msg]
  (print msg))

(defn error [msg]
  (info (str "Error " msg)))

(defn kv [k v]
  (let [v (if (= (.toString v) "[object String]")
            (.stringify js/JSON v)
            v)]
    (str k "=" v "\n")))

(defn new-mic []
  (let [m (.Microphone (new js/Wit)
            (.getElementById js/document "microphone"))])
  (set! m.onready (fn [] (info "Microphone is ready to record")))
  (set! m.onaudiostart (fn [] (info "Recording started")))
  (set! m.onaudioend (fn [] (info "Recording stopped, processing started")))
  (set! m.onerror (fn [err] (error err)))
  (set! m.onconnecting (fn [] (info "Microphone is connecting")))
  (set! m.ondisconnected (fn [] (info "Microphone is not connected")))
  (set! m.onresult (fn [intent, entities] (info intent) (info entities)))
  m)

(def mic (new-mic))

;; ──────────────────────────────────────────────────────────────────────
;; state

(defonce state
  (atom {:cart {}
         :text "Hello Chestnut!"
         }))


;; ──────────────────────────────────────────────────────────────────────
;; Main

(def intro "Hello, what can I help you find today?")
(def api-token "X45BKBNG6KKPYUVAWGYQZ3YG2VE3GHZF")

(defn main []
  (.connect mic api-token)
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (:text app)))))
    state
    {:target (. js/document (getElementById "app"))}))
