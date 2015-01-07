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

;; ──────────────────────────────────────────────────────────────────────
;; State

(def state
  (atom {:cart []
         :Items []}))

;; ──────────────────────────────────────────────────────────────────────
;; Utils & Settings

;;This is bound to a specific domain
(def *api-token* "X45BKBNG6KKPYUVAWGYQZ3YG2VE3GHZF")

(defn format-price [n]
  (str "$" (.toFixed (/ (Math.round (* n 100)) 100) 2)))

(defn log [s]
  (.log js/console s))

;; ──────────────────────────────────────────────────────────────────────
;; Relay Foods API

(defn search [q]
  (log (str "Searching for " q))
  (http/post "https://api.relayfoods.com/api/ecommerce/v1/DCX/search/"
    {:with-credentials? false
     :form-params {"Query" (apply str (interpose " " (map #(str "query:" %) q)))
                   "Page" 1, "PageSize" 20, "RefinementSize" 5
                   "Sort" 2, "Heavy" false}}))

(defn best-match [q]
  (log (str "Best match search for " q))
  (go
    (first (get-in (<! (search q)) [:body :Items]))))

;; ──────────────────────────────────────────────────────────────────────
;; Conversation / AI

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

(defn say [msg]
  (.speak js/speechSynthesis (new js/SpeechSynthesisUtterance msg)))

(defn fill-space []
  (say (rand-nth dead-space-fillers)))

(defn ask-again []
  (say (rand-nth second-chance)))

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

(defn normalize
  "The structure of the entities can vary widely, first extract the
  correct entity(ies) by the supplied key, then they may be an object,
  or a vector of objects, then the values may be a single word, or a
  phrase. Normalize all into a list of words."
  [entities key]
  (flatten
    (map #(clojure.string.split % #"\s+")
      (map :value (flatten (conj [] (key entities)))))))

(defn interact [intent entities]
  (case intent
    "add_to_cart" (add-to-cart (normalize entities :product))
    "search" (interactively-refine-search (normalize entities :local_search_query))))

;; ──────────────────────────────────────────────────────────────────────
;; Wit.ai

(defn new-mic []
  (let [m (new js/Wit.Microphone (.getElementById js/document "microphone"))]
    (set! (.-onready m) (fn [] (log "Microphone is ready to record")))
    (set! (.-onaudiostart m) (fn [] (log "Recording started")))
    (set! (.-onaudioend m) (fn []
                             (log "Recording stopped, processing started")
                             (fill-space)))
    (set! (.-onerror m) (fn [err]
                          (log (str "Error: " err))
                          (ask-again)))
    (set! (.-onconnecting m) (fn [] (log "Microphone is connecting")))
    (set! (.-ondisconnected m) (fn [] (log "Microphone is not connected")))
    (set! (.-onresult m) (fn [intent entities]
                           (let [entities (js->clj entities
                                            :keywordize-keys true)]
                             (log "Result returned")
                             (log (str "Intent of " intent))
                             (log (str "Entities " entities))
                             (interact intent entities))))
    m))

(defcomponent microphone
  "DOM required by the Wit.Microphone library"
  [data owner]
  (render [_]
    (dom/div {:id "microphone"})))

;; ──────────────────────────────────────────────────────────────────────
;; Main

(defn image
  ([name] (image name 150 200))
  ([name w h]
     (str "https://res.cloudinary.com/relay-foods/image/upload/"
       "q_40,h_" h ",w_" w ",c_fill/" name ".JPG")))

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
        (dom/div {:class "col-xs-offset-4 col-xs-5 focus"}
          (om/build microphone data)))
      (om/build cart data)
      (dom/div {:class "row"}
        (om/build available-products data)))))

(defn main []
  (om/root app state {:target (. js/document (getElementById "app"))})
  (def mic (new-mic))
  (.connect mic *api-token*)
  (say intro))
