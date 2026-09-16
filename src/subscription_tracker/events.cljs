;; [[file:../../README.org::*NS: subscription-tracker.events][NS: subscription-tracker.events:1]]
(ns subscription-tracker.events
  (:require
   [re-frame.core :as re-frame]
   [cljs.spec.alpha :as s]
   [subscription-tracker.db :as db]
   [subscription-tracker.localstorage :as localstorage]
   [day8.re-frame.tracing :refer-macros [fn-traced]]))

;; -- Interceptors ------------------------------------------------------------

(defn check-and-throw
  "Throws an exception if `db` doesn't match the given spec."
  [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "Spec check failed: " (s/explain-str spec db))
                     {:spec spec :db db}))))

(def check-spec-interceptor
  "Validates app-db against ::db/db after every event; throws if invalid."
  (re-frame/after (partial check-and-throw ::db/db)))

(re-frame/reg-cofx
 :local-store
 (fn [cofx _]
   (assoc cofx :local-store (localstorage/load-data))))

(def ->local-store
  "Persists the :subscriptions/:accounts portion of app-db to localStorage after every event."
  (re-frame/after
   (fn [db _]
     (localstorage/store-data! (select-keys db [:subscriptions :accounts])))))

;; -- Events --------------------------------------------------------------

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :local-store)
  check-spec-interceptor
  ->local-store]
 (fn-traced [{:keys [local-store]} _]
            {:db (merge db/default-db local-store)}))
;; NS: subscription-tracker.events:1 ends here
