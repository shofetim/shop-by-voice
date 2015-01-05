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

(def *audio* false)
(def *api-token* "X45BKBNG6KKPYUVAWGYQZ3YG2VE3GHZF")

(defn ^:export say [msg]
  (if *audio*
    (.speak js/speechSynthesis (new js/SpeechSynthesisUtterance msg))
    (.log js/console msg)))

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
  (let [m (new js/Wit.Microphone (.getElementById js/document "microphone"))]
    (set! (.-onready m) (fn [] (info "Microphone is ready to record")))
    (set! (.-onaudiostart m) (fn [] (info "Recording started")))
    (set! (.-onaudioend m) (fn [] (info "Recording stopped, processing started")))
    (set! (.-onerror m) (fn [err] (error err)))
    (set! (.-onconnecting m) (fn [] (info "Microphone is connecting")))
    (set! (.-ondisconnected m) (fn [] (info "Microphone is not connected")))
    (set! (.-onresult m) (fn [intent, entities] (info intent) (info entities)))
    m))

;; ──────────────────────────────────────────────────────────────────────
;; state

(defonce state
  (atom {:cart {}
         :view :main
         }))

;; ──────────────────────────────────────────────────────────────────────
;; Main

(defn handle-input [data owner]
  (let [node (om/get-node owner "query")]
    (.log js/console (.-value node))
    (set! (.-value node) "")))

(defcomponent input [data owner]
  (render [_]
    (i/input {:type "text" :ref "query"
              :auto-focus true
              :placeholder "Hello, what can I help you find today?"
              :bs-style "primary"
              :on-key-down #(when (= (.-key %) "Enter")
                              (handle-input data owner))})))


(def intro "Hello, what can I help you find today?")


(defcomponent app [data owner]
  (render [_]
    (dom/div {:class "container-fluid"}
      (case (:view data)
        :main (om/build input data)))))

(defn main []
  ;; (def mic (new-mic))
  ;; (.connect mic *api-token*)
  (om/root app state {:target (. js/document (getElementById "app"))}))
