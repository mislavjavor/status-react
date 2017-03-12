(ns status-im.chat.views.input.input
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [taoensso.timbre :as log]
            [status-im.accessibility-ids :as id]
            [status-im.components.react :refer [view
                                                animated-view
                                                text
                                                scroll-view
                                                text-input
                                                icon
                                                touchable-highlight
                                                dismiss-keyboard!]]
            [status-im.chat.models.input :as input-model]
            [status-im.chat.views.input.emoji :as emoji]
            [status-im.chat.views.input.parameter-box :as parameter-box]
            [status-im.chat.views.input.result-box :as result-box]
            [status-im.chat.views.input.suggestions :as suggestions]
            [status-im.chat.views.input.validation-messages :as validation-messages]
            [status-im.chat.styles.input.input :as style]
            [status-im.chat.utils :as utils]
            [status-im.chat.constants :as const]
            [status-im.components.animation :as anim]
            [status-im.i18n :as i18n]))

(defn command-view [index {command-name :name :as command}]
  [touchable-highlight {:on-press #(dispatch [:select-chat-input-command command nil])}
   [view
    [text {:style (style/command (= index 0))
           :font  :roboto-mono}
     (str const/command-char) command-name]]])

(defview commands-view []
  [commands [:chat :command-suggestions]
   show-suggestions? [:show-suggestions?]]
  [view style/commands-root
   [view style/command-list-icon-container
    [touchable-highlight {:on-press #(do (dispatch [:toggle-chat-ui-props :show-suggestions?])
                                         (dispatch [:set-chat-ui-props :validation-messages nil])
                                         (dispatch [:update-suggestions]))}
     [view style/commands-list-icon
      (if show-suggestions?
        [icon :close_gray style/close-commands-list-icon]
        [icon :input_list style/commands-list-icon])]]]
   [scroll-view {:horizontal                     true
                 :showsHorizontalScrollIndicator false}
    [view style/commands
     (for [[index [command-key command]] (map-indexed vector commands)]
       ^{:key command-key}
       [command-view index command])]]])

(defn- invisible-input [{:keys [set-layout-width value]}]
  [text {:style     style/invisible-input-text
         :on-layout #(let [w (-> (.-nativeEvent %)
                                 (.-layout)
                                 (.-width))]
                       (set-layout-width w))}
   (utils/safe-trim value)])

(defn- input-helper [_]
  (let [input-text (subscribe [:chat :input-text])]
    (fn [{:keys [command width]}]
      (when-let [placeholder (cond
                               (= @input-text const/command-char)
                               (i18n/label :t/type-a-command)

                               (and command (empty? (:args command)))
                               (get-in command [:command :params 0 :placeholder])

                               (and command
                                    (= (count (:args command)) 1)
                                    (input-model/text-ends-with-space? @input-text))
                               (get-in command [:command :params 1 :placeholder]))]
        [text {:style (style/input-helper-text width)}
         placeholder]))))

(defn input-view [_]
  (let [component            (r/current-component)
        set-layout-width     #(r/set-state component {:width %})
        set-layout-height    #(r/set-state component {:height %})
        input-text           (subscribe [:chat :input-text])
        modified-text        (subscribe [:chat :modified-text])
        command              (subscribe [:selected-chat-command])
        sending-in-progress? (subscribe [:chat-ui-props :sending-in-progress?])]
    (r/create-class
      {:component-will-mount
       (fn []
         (dispatch [:update-suggestions]))

       :reagent-render
       (fn [{:keys [anim-margin]}]
         (let [{:keys [width height]} (r/state component)
               command @command]
           [animated-view {:style (style/input-root height anim-margin)}
            [text-input
             {:ref                    #(dispatch [:set-chat-ui-props :input-ref %])
              :accessibility-label    id/chat-message-input
              :blur-on-submit         true
              :multiline              true
              :default-value          (or @modified-text @input-text "")
              :editable               (not @sending-in-progress?)
              :on-blur                #(do (dispatch [:set-chat-ui-props :input-focused? false])
                                           (set-layout-height 0))
              :on-change-text         #(when-not (str/includes? % "\n")
                                         (do (dispatch [:set-chat-input-text %])
                                             (dispatch [:load-chat-parameter-box (:command command)])
                                             (when (not command)
                                               (dispatch [:set-chat-input-metadata nil])
                                               (dispatch [:set-chat-ui-props :result-box nil]))
                                             (dispatch [:set-chat-ui-props :validation-messages nil])))
              :on-content-size-change #(let [h (-> (.-nativeEvent %)
                                                   (.-contentSize)
                                                   (.-height))]
                                         (set-layout-height h))
              :on-selection-change    #(let [s (-> (.-nativeEvent %)
                                                   (.-selection))]
                                         (dispatch [:set-chat-ui-props :selection {:start (.-start s)
                                                                                   :end   (.-end s)}]))
              :on-submit-editing      #(do (dispatch [:set-chat-ui-props :sending-in-progress? true])
                                           (dispatch [:send-current-message]))
              :on-focus               #(do (dispatch [:set-chat-ui-props :input-focused? true])
                                           (dispatch [:set-chat-ui-props :show-emoji? false]))
              :style                  (style/input-view height)
              :placeholder-text-color style/color-input-helper-placeholder}]
            [invisible-input {:value            (or @modified-text @input-text "")
                              :set-layout-width set-layout-width}]
            [input-helper {:command command
                           :width   width}]
            (if-not command
              [touchable-highlight
               {:on-press #(do (dispatch [:toggle-chat-ui-props :show-emoji?])
                               (dismiss-keyboard!))}
               [view
                [icon :smile style/input-emoji-icon]]]
              [touchable-highlight
               {:on-press #(do (dispatch [:set-chat-input-text nil])
                               (dispatch [:set-chat-input-metadata nil])
                               (dispatch [:set-chat-ui-props :result-box nil])
                               (dispatch [:set-chat-ui-props :validation-messages nil]))}
               [view style/input-clear-container
                [icon :close_gray style/input-clear-icon]]])]))})))

(defview input-container [{:keys [anim-margin]}]
  [command-complete? [:command-complete?]
   selected-command [:selected-chat-command]
   input-text [:chat :input-text]]
  [view style/input-container
   [input-view {:anim-margin anim-margin}]
   (when (and (not (str/blank? input-text))
              (or command-complete? (not selected-command)))
     [touchable-highlight {:on-press #(do (dispatch [:set-chat-ui-props :sending-in-progress? true])
                                          (dispatch [:send-current-message]))}
      [view style/send-message-container
       [icon :arrow_top style/send-message-icon]]])])

(defn container []
  (let [margin                (subscribe [:chat-input-margin])
        show-emoji?           (subscribe [:chat-ui-props :show-emoji?])
        input-text            (subscribe [:chat :input-text])
        anim-margin           (anim/create-value 10)
        container-anim-margin (anim/create-value 16)
        bottom-anim-margin    (anim/create-value 14)]
    (r/create-class
      {:component-did-mount
       (fn []
         (when-not (str/blank? @input-text)
           (.setValue anim-margin 0)
           (.setValue container-anim-margin 8)
           (.setValue bottom-anim-margin 8)))
       :component-did-update
       (fn [component]
         (let [{:keys [text-empty?]} (reagent.core/props component)]
           (let [to-anim-value           (if text-empty? 10 0)
                 to-container-anim-value (if text-empty? 16 8)
                 to-bottom-anim-value    (if text-empty? 14 8)]
             (anim/start
               (anim/timing anim-margin {:toValue  to-anim-value
                                         :duration 100}))
             (anim/start
               (anim/timing container-anim-margin {:toValue  to-container-anim-value
                                                   :duration 100}))
             (anim/start
               (anim/timing bottom-anim-margin {:toValue  to-bottom-anim-value
                                                :duration 100})))))

       :reagent-render
       (fn []
         [view
          [parameter-box/parameter-box-view]
          [result-box/result-box-view]
          [suggestions/suggestions-view]
          [validation-messages/validation-messages-view]
          [view {:style     (style/root @margin)
                 :on-layout #(let [h (-> (.-nativeEvent %)
                                         (.-layout)
                                         (.-height))]
                               (dispatch [:set-chat-ui-props :input-height h]))}
           [animated-view {:style (style/container container-anim-margin bottom-anim-margin)}
            (when (str/blank? @input-text)
              [commands-view])
            [input-container {:anim-margin anim-margin}]]
           (when @show-emoji?
             [emoji/emoji-view])]])})))
