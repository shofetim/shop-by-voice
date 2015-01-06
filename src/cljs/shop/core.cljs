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
            [cljs-http.client :as http]
            [shop.secret :refer [*secret*]]))

(enable-console-print!)

;; ──────────────────────────────────────────────────────────────────────
;; Utils

(def *audio* true)
(def *api-token* "X45BKBNG6KKPYUVAWGYQZ3YG2VE3GHZF")

(def intro "Hello, what can I help you find today?")

(def dead-space-fillers
  ["Humm, let me see..."
   "Ok, one second."
   "I'm not sure, let me check."
   "Let me see what we have in stock"
   "Checking, I'll be right back with you."
   "I think we do. I will check."])

(def second-chance
  ["I am sorry, I couldn't understand you. Could repeat that?"
   "I am having trouble understanding you. Could you repeat that?"
   "Sorry, I didn't catch that, please try again."
   "I'm sorry, I couldn't understand you. Could you say it in another way?"
   ])

(defn ^:export say [msg]
  (if *audio*
    (.speak js/speechSynthesis (new js/SpeechSynthesisUtterance msg))
    (.log js/console msg)))

(defn ^:export fill-space []
  (say (rand-nth dead-space-fillers)))

(defn ^:export ask-again []
  (say (rand-nth second-chance)))

;; ──────────────────────────────────────────────────────────────────────
;; Wit.ai

(defn info [msg]
  ( msg))

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

;; Cross domain, simple text interaction with Wit.ai, to get this to
;; work run chrome with `google-chrome --disable-web-security`
(defn wit-send [msg]
  (http/get "https://api.wit.ai/message"
    ;;The other token is tied to a domain, this one isn't so keep it
    ;;out of git's history
    {:headers {"Authorization" (str "Bearer " *secret*)}
     :query-params {"v" gensym, "q" msg}}))

;; ──────────────────────────────────────────────────────────────────────
;; Relay Foods

(defn ^:export search [q]
  (http/post "https://api.relayfoods.com/api/ecommerce/v1/DCX/search/"
    {:form-params {"Query" (str "query:" q), "Page" 1
                    "PageSize" 20, "RefinementSize" 5
                    "Sort" 2, "Heavy" false}}))

;; ;; One ugly mock
;; (defn ^:export search [q]
;;   (go (js->clj
;;         (.parse js/JSON
;;           (:body
;;            (<!
;;              (http/post "http://localhost:8000/search"
;;                {:form-params {"Query" (str "query:" "eggs"), "Page" 1
;;                               "PageSize" 20, "RefinementSize" 5
;;                               "Sort" 2, "Heavy" false}}))))
;;         :keywordize-keys true)))

;; ──────────────────────────────────────────────────────────────────────
;; state

;;defonce
(def state
  (atom {:cart []
         :Items []}))

;; ──────────────────────────────────────────────────────────────────────
;; Main

(defn best-match [q]
  (go
    (first (get-in (<! (search q)) [:body :Items]))))

(defn acknowledge-add-to-cart [p]
  (say
    (str
      "Thank you. I have added "
      (get-in p [:Brand :Name]) "'s"
      (get-in p [:Name])
      " to your cart"))
  (let [h (:Hearts p)]
    (cond
      (> h 100) (say
                  (str
                    "Wow, great minds think a like. "
                    "That is one of our most beloved foods,"))
      (> h 50) (say "That is a good choice"))))

(defn add-to-cart [q]
  (go
    (let [prod (<! (best-match q))]
      (swap! state assoc :cart
        (conj (@state :cart) prod))
      (acknowledge-add-to-cart prod))))

(defn interactively-refine-search [q]
  (go
    (let [response (<! (search q))
          status (:status response)
          products (get-in response [:body :Items])]
      (if (= status 200)
        (do
          (swap! state assoc :Items products)
          (say
            (str
              "We do have several products like that. "
              "Could I interest you in "
              (:Name (first products))
              " or "
              (:Name (second products))
              " or perhaps maybe "
              (:Name (nth products 2)))))
        (say "I'm sorry, I've having trouble searching for that. Please try again")))))

(defn interact [response]
  (let [outcomes (get-in response [:body :outcomes])
        outcome (first outcomes)
        confidence (:confidence outcome)
        intent-of (:intent outcome)
        entities (:entities outcome)]
    (fill-space)
    (if (< confidence 0.27)
      (ask-again)
      ;;fallback to other entity?
      (case intent-of
        "add_to_cart" (add-to-cart
                        (->> entities :product first :value))
        "search" (interactively-refine-search
                   (->> entities :local_search_query first :value))))))

(defn handle-input [data owner]
  (let [node (om/get-node owner "query")
        query (.-value node)]
    (go (let [response (<! (wit-send query))]
          (prn (:status response))
          (prn (:body response))
          (interact response)))
    (set! (.-value node) "")))

(defcomponent input [data owner]
  (render [_]
    (i/input {:type "text" :ref "query"
              :auto-focus true
              :placeholder "Hello, what can I help you find today?"
              :bs-style "primary"
              :on-key-down #(when (= (.-key %) "Enter")
                              (handle-input data owner))})))

(defn image
  ([name] (image name 150 200))
  ([name w h]
     (str "https://res.cloudinary.com/relay-foods/image/upload/"
       "q_40,h_" h ",w_" w ",c_fill/" name ".JPG")))


(defn format-price [n]
  (str "$" (.toFixed (/ (Math.round (* n 100)) 100) 2)))

(defcomponent available-products [data owner]
  (render [_]
    (dom/div {:class "col-xs-9 available-products"}
      (dom/h2 "Available Products")
      (dom/div
        (map #(dom/div {:class "flip-container"}
                (dom/div {:class "flipper"}
                  (dom/div {:class "front"}
                    (dom/img  {:src (image (get-in % [:Image :Filename]))}))
                  (dom/div {:class "back"}
                    (dom/span {:class "brand"} (get-in % [:Brand :Name]))
                    (dom/span {:class "name"} (get-in % [:Name]))
                    (dom/span {:class "price"} (format-price (->> % :Variants first :Price))))))
          (:Items data))))))

(defcomponent cart [data owner]
  (render [_]
    (dom/div {:class "col-xs-3 pull-right cart"}
      (dom/h2 "Cart")
      (dom/ul {:class "list-group"}
        (map #(dom/li {:class "list-group-item "}
                (dom/img  {:src (image (get-in % [:Image :Filename]) 67 53)})
                (dom/span {:class "brand"} (get-in % [:Brand :Name]))
                (dom/span {:class "name"} (get-in % [:Name])))
          (:cart data))))))

(defcomponent app [data owner]
  (render [_]
    (dom/div {:class "container-fluid"}
      (dom/div {:class "row"}
        (dom/div {:class "col-xs-offset-3 col-xs-6 focus"}
          (om/build input data)))
      (om/build cart data)
      (dom/div {:class "row"}
        (om/build available-products data)))))

(defn ^:export dump []
  (.log js/console (clj->js @state)))

(defn main []
  ;; (def mic (new-mic))
  ;; (.connect mic *api-token*)
  (say intro)
  (om/root app state {:target (. js/document (getElementById "app"))}))
