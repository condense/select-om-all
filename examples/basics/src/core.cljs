(ns examples.basics.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async
             :refer [>! <! alts! chan put! timeout]]
            [goog.net.Jsonp]
            [goog.Uri]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [select-om-all.core :refer [AutoComplete]]
            [figwheel.client :as fw]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(def base-url
  "http://en.wikipedia.org/w/api.php?action=opensearch&format=json&search=")

(defn jsonp
  ([uri] (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (goog.net.Jsonp. (goog.Uri. uri))]
     (.send gjsonp nil #(put! c %))
     c)))

(defn wikipedia-search [query]
  (go (let [;; simulate slow network
            ;_ (<! (timeout 2000))
            resp (<! (jsonp (str base-url query)))]
        (apply mapv vector (rest resp)))))

(defn App [{:keys [choice1 choice2 hl1 hl2] :as props} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.container
        [:h2.text-center "AutoComplete Demo"]
        [:hr]
        [:div {:style {:width 800
                       :display "inline-block"}}
         (om/build AutoComplete {:completions  wikipedia-search
                                 :array?       true
                                 :flex         [1 3 2]
                                 :throttle     750
                                 :placeholder  "Select mode, remote data, multiple columns"
                                 :height       250
                                 :on-change    #(om/update! props :choice1 (first %))
                                 :on-highlight #(om/update! props :hl1 %)})]
        [:span " Choice:" choice1]
        [:span " | Highlight:" hl1]
        [:hr]
        [:div {:style {:width  400
                       :display "inline-block"}}
         (om/build AutoComplete {:cursor      (:datasource props)
                                 :default     (-> props :datasource (get 10))
                                 :placeholder "Select mode with default value"})]
        [:div {:style {:height 300}}]
        [:p "On the bottom of viewport, popup should pop... up ;-)"]
        [:div {:style {:width  400
                       :display "inline-block"}}
         (om/build AutoComplete {:cursor       (:datasource props)
                                 :editable?    true
                                 :placeholder  "Edit mode, local data, one column"
                                 :on-change    #(om/update! props :choice2 (first %))
                                 :on-highlight #(om/update! props :hl2 %)})]
        [:span " Choice:" choice2]
        [:span " | Highlight:" hl2]]))))

(def AZ (mapv js/String.fromCharCode (range 65 91)))

(defn rand-str [n]
  (apply str (repeatedly n #(rand-nth AZ))))

(def app-state
  (atom {:datasource (vec (repeatedly 1000 #(rand-str 20)))}))

(om/root App app-state {:target (js/document.getElementById "app")})

;;; aux

(fw/start {:websocket-url "ws://localhost:3449/figwheel-ws"})