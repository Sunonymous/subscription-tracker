;; [[file:../../README.org::*NS: subscription-tracker.core][NS: subscription-tracker.core:1]]
(ns subscription-tracker.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [subscription-tracker.events :as events]
   [subscription-tracker.views :as views]
   [subscription-tracker.config :as config]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
;; NS: subscription-tracker.core:1 ends here
