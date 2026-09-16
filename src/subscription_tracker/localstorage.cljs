;; [[file:../../README.org::*NS: subscription-tracker.localstorage][NS: subscription-tracker.localstorage:1]]
(ns subscription-tracker.localstorage
  (:require [cljs.reader :as reader]))

(def ls-key
  "localStorage key under which the whole app's data is persisted."
  "subscription-tracker/app-db")

(defn store-data!
  "Writes `data` into localStorage as an edn string."
  [data]
  (.setItem js/localStorage ls-key (pr-str data)))

(defn load-data
  "Reads previously persisted data from localStorage, if any."
  []
  (when-let [stored (.getItem js/localStorage ls-key)]
    (reader/read-string stored)))
;; NS: subscription-tracker.localstorage:1 ends here
