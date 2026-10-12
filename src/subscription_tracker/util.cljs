;; [[file:../../README.org::*NS: subscription-tracker.util][NS: subscription-tracker.util:1]]
(ns subscription-tracker.util)

(defn annual-occurrences
  "How many times per year a subscription bills."
  [{:keys [frequency custom-interval-days]}]
  (case frequency
    :weekly 52
    :monthly 12
    :quarterly 4
    :yearly 1
    :custom (if (and custom-interval-days (pos? custom-interval-days))
              (/ 365 custom-interval-days)
              0)))

(defn annual-cost [subscription]
  (* (:cost subscription) (annual-occurrences subscription)))

(defn monthly-cost [subscription]
  (/ (annual-cost subscription) 12))

(defn- pad2 [n]
  (if (< n 10) (str "0" n) (str n)))

(defn- today-str []
  (let [d (js/Date.)]
    (str (.getFullYear d) "-" (pad2 (inc (.getMonth d))) "-" (pad2 (.getDate d)))))

(defn past-end-date?
  "True when `subscription`'s :end-date is set and has already passed."
  [{:keys [end-date]}]
  (and end-date (neg? (compare end-date (today-str)))))
;; NS: subscription-tracker.util:1 ends here
