;; [[file:../../README.org::*NS: subscription-tracker.views][NS: subscription-tracker.views:1]]
(ns subscription-tracker.views
  (:require
   [re-frame.core :as re-frame]
   [subscription-tracker.subs :as subs]))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div
     [:h1
      "Hello from " @name]]))
;; NS: subscription-tracker.views:1 ends here
