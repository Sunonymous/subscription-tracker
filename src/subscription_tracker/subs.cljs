;; [[file:../../README.org::*NS: subscription-tracker.subs][NS: subscription-tracker.subs:1]]
(ns subscription-tracker.subs
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [subscription-tracker.util :as util]))

;; -- Layer 2: extractors -----------------------------------------------------

(re-frame/reg-sub
 ::subscriptions
 (fn [db _]
   (:subscriptions db)))

(re-frame/reg-sub
 ::accounts
 (fn [db _]
   (:accounts db)))

(re-frame/reg-sub
 ::categories
 (fn [db _]
   (:categories db)))

;; -- Layer 3: derived ---------------------------------------------------------

(re-frame/reg-sub
 ::subscription-list
 :<- [::subscriptions]
 (fn [subscriptions _]
   (vals subscriptions)))

(re-frame/reg-sub
 ::subscription
 :<- [::subscriptions]
 (fn [subscriptions [_ id]]
   (get subscriptions id)))

(re-frame/reg-sub
 ::account-list
 :<- [::accounts]
 (fn [accounts _]
   (vals accounts)))

(re-frame/reg-sub
 ::account
 :<- [::accounts]
 (fn [accounts [_ id]]
   (get accounts id)))

(re-frame/reg-sub
 ::subscriptions-for-account
 :<- [::subscriptions]
 (fn [subscriptions [_ account-id]]
   (filter #(= (:account-id %) account-id) (vals subscriptions))))

(re-frame/reg-sub
 ::category-names
 :<- [::categories]
 (fn [categories _]
   (sort (keys categories))))

(re-frame/reg-sub
 ::category-color
 :<- [::categories]
 (fn [categories [_ category-name]]
   (get-in categories [category-name :color])))

(re-frame/reg-sub
 ::active-subscriptions
 :<- [::subscription-list]
 (fn [subscriptions _]
   (filter #(and (= (:status %) :active) (not (util/past-end-date? %))) subscriptions)))

(re-frame/reg-sub
 ::active-subscription-count
 :<- [::active-subscriptions]
 (fn [active _]
   (count active)))

(re-frame/reg-sub
 ::total-monthly-cost
 :<- [::active-subscriptions]
 (fn [active _]
   (reduce + 0 (map util/monthly-cost active))))

(re-frame/reg-sub
 ::total-yearly-cost
 :<- [::active-subscriptions]
 (fn [active _]
   (reduce + 0 (map util/annual-cost active))))

;; -- Phase I: filtering & sorting ---------------------------------------------

(re-frame/reg-sub
 ::ui
 (fn [db _]
   (:ui db)))

(re-frame/reg-sub
 ::sort-by
 :<- [::ui]
 (fn [ui _] (:sort-by ui)))

(re-frame/reg-sub
 ::sort-dir
 :<- [::ui]
 (fn [ui _] (:sort-dir ui)))

(re-frame/reg-sub
 ::status-filter
 :<- [::ui]
 (fn [ui _] (:status-filter ui)))

(re-frame/reg-sub
 ::category-filter
 :<- [::ui]
 (fn [ui _] (:category-filter ui)))

(re-frame/reg-sub
 ::search
 :<- [::ui]
 (fn [ui _] (:search ui)))

(defn- sort-value
  "Resolves the field `sort-by` actually refers to on a subscription."
  [sort-by {sub-name :name :keys [status] :as subscription}]
  (case sort-by
    :cost (util/monthly-cost subscription)
    :status (name status)
    (str/lower-case sub-name)))

(re-frame/reg-sub
 ::filtered-subscriptions
 :<- [::subscription-list]
 :<- [::search]
 :<- [::status-filter]
 :<- [::category-filter]
 :<- [::sort-by]
 :<- [::sort-dir]
 (fn [[subscriptions search status-filter category-filter sort-by sort-dir] _]
   (let [search (str/lower-case (or search ""))
         matches? (fn [{sub-name :name :keys [status category]}]
                    (and (or (str/blank? search) (str/includes? (str/lower-case sub-name) search))
                         (or (nil? status-filter) (= status status-filter))
                         (or (nil? category-filter) (= category category-filter))))
         inactive? (fn [sub] (or (= (:status sub) :cancelled) (util/past-end-date? sub)))
         rank (fn [sub] [(inactive? sub) (sort-value sort-by sub)])
         comparator (fn [a b]
                      (let [[a-cancelled? a-val] (rank a)
                            [b-cancelled? b-val] (rank b)]
                        (cond
                          (not= a-cancelled? b-cancelled?) (compare a-cancelled? b-cancelled?)
                          (= sort-dir :desc) (compare b-val a-val)
                          :else (compare a-val b-val))))]
     (->> subscriptions
          (filter matches?)
          (sort comparator)))))

;; -- Phase J: import/export ---------------------------------------------------

(re-frame/reg-sub
 ::export-data
 :<- [::subscriptions]
 :<- [::accounts]
 :<- [::categories]
 (fn [[subscriptions accounts categories] _]
   {:subscriptions subscriptions :accounts accounts :categories categories}))

(re-frame/reg-sub
 ::import-error
 :<- [::ui]
 (fn [ui _] (:import-error ui)))
;; NS: subscription-tracker.subs:1 ends here
