;; [[file:../../README.org::*NS: subscription-tracker.subs][NS: subscription-tracker.subs:1]]
(ns subscription-tracker.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))
;; NS: subscription-tracker.subs:1 ends here
