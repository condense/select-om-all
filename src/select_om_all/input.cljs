(ns select-om-all.input
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<! put!] :as a]
            [cljs.core.match :refer-macros [match]]
            [clojure.string :refer [blank?]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [select-om-all.utils :refer [handle-key-down relevant-keys KEYS
                                         ESC BKSP UP_ARROW DOWN_ARROW]]))

;;; INPUT COMPONENT must deal with:
;;; PROPS
;;; placeholder — placeholder text
;;; editable? — allow input to contain non-selected items
;;; default — initial item
;;; display-fn — function to convert item (incl. default) to its representation
;;; undisplay-fn — when `editable?` convert user input to item
;;; initial-loading? — lock input while waiting for remote data outside of component
;;; simple? — mimic traditional select without search input (but probably listening for keys to filter options)
;;; STATE
;;; text — input value
;;; refocus — control channel, when to focus back on itself (if necessary for succesful input)
;;; focus — put! :focus when ready to read user input for autocomplete
;;; blur — put! :blur when suggestions list should be dismissed
;;; input — put! query content here
;;; keycodes — put! raw user input
;;; selecting? — atom of selecting state, to specially handle keys if necessary
;;; hold? — atom, set to true when blur of all other AutoComplete components must be ignored
;;; open? — is completions list open?
;;; value — current item
;;; autocompleter — channel with completion result, put! (undisplay-fn text) here in Edit mode blur

;;; Default input component implementation

(defn display [display-fn value]
  (if (= value :select-om-all.logic/none) "" (display-fn value)))

(defn Input [{:keys [placeholder editable? default display-fn undisplay-fn
                     initial-loading? disabled? simple?]
              :or   {display-fn identity
                     undisplay-fn identity}} owner]
  (reify
    om/IDisplayName (display-name [_] "AutoComplete Input")
    om/IDidMount
    (did-mount [_]
      (go-loop []
        (when-let [_ (<! (om/get-state owner :refocus))]
          (.focus (om/get-node owner "input"))
          (recur))))
    om/IRenderState
    (render-state [_ {:keys [focus refocus blur input keycodes autocompleter
                             open? hold? selecting? value typing]}]
      (when-not open?
        (om/set-state-nr! owner :typing nil))
      (let [id (str (gensym))
            display-fn (partial display display-fn)]
        (html
         [:div.has-feedback
          [:label.control-label.sr-only {:for id}]
          [:input.form-control
           {:ref            "input"
            :id             id
            :style          {:width "100%"
                             :padding-right 42
                             :text-overflow "ellipsis"}
            :type           "text"
            :placeholder    (if initial-loading? "Loading..." placeholder)
            :disabled       (or disabled? initial-loading?)
            :read-only       (when simple? "readonly")
            :default-value  (display-fn default)
            :value          (if (and open? typing (not simple?))
                              typing
                              (display-fn value))
            :on-focus       (fn [_]
                              (when-not open?
                                (put! focus :focus)
                                (when-not editable? (put! input "")))
                              true)
            :on-mouse-up    (fn [e]
                              (when-not editable?
                                (let [t (.-target e)]
                                  (.setSelectionRange
                                   t 0 (.. t -value -length)))))
            :on-mouse-down  (when-not editable?
                              (fn [_]
                                (if open?
                                  (put! keycodes ESC)
                                  (put! input ""))
                                true))
            :on-blur        #(do
                               (when editable?
                                 (put! autocompleter (undisplay-fn (.. % -target -value))))
                               (put! blur :blur) true)
            :on-input       (when-not simple?
                              #(let [v (.. % -target -value)]
                                 (om/set-state! owner :typing v)
                                 (put! input v)
                                 true))
            :on-key-down    #(do
                               (handle-key-down keycodes selecting? hold? %)
                               (when simple?
                                 (let [kc (.-keyCode %)]
                                   (when (#{UP_ARROW DOWN_ARROW} kc)
                                     (.preventDefault %))
                                   (when (relevant-keys kc)
                                     (let [v (or (om/get-state owner :typing) "")
                                           v (if (= kc BKSP)
                                               (subs v 0 (dec (count v)))
                                               (str v (js/String.fromCharCode kc)))]
                                       (om/set-state! owner :typing v)
                                       (put! input v)))))
                               true)
            :on-mouse-enter #(reset! hold? true)
            :on-mouse-leave #(do (reset! hold? false) true)}]
          (when-not (or open? disabled?
                        (blank? value)
                        (= :select-om-all.logic/none value))
            [:span.glyphicon.glyphicon-remove.form-control-feedback
             {:style {:right          17
                      :color          "#aaa"
                      :pointer-events "inherit"
                      :cursor         "pointer"}
              :on-mouse-down
              #(put! autocompleter :select-om-all.logic/none)}])
          [:span.glyphicon.form-control-feedback
           {:class (str "glyphicon-chevron-" (if open? "up" "down"))
            :style {:pointer-events "inherit"
                    :cursor         "pointer"}
            :on-mouse-down
            (fn []
              (if open?
                (reset! hold? false)
                (do
                  (let [t (om/get-node owner "input")]
                    (.setSelectionRange t 0 (.. t -value -length)))
                  (put! input "")
                  (put! refocus true)))
              true)}]])))))