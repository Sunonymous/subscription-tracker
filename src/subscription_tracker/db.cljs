;; [[file:../../README.org::*NS: subscription-tracker.db][NS: subscription-tracker.db:1]]
(ns subscription-tracker.db
  (:require [cljs.spec.alpha :as s]))

(def default-account
  "Shape of a single account entity: where a subscription's money comes from."
  {:id nil
   :name ""
   :last-digits ""
   :created-at nil
   :updated-at nil})

(def default-subscription
  "Shape of a single subscription entity. See README.org schema notes."
  {:id nil
   :name ""
   :cost 0
   :frequency :monthly            ;; #{:weekly :monthly :quarterly :yearly :custom}
   :custom-interval-days nil
   :billing-day nil               ;; 1-31; monthly/quarterly/yearly
   :billing-month nil             ;; 1-12; yearly only (day alone is ambiguous)
   :billing-weekday nil           ;; day-of-week keyword; weekly only
   :status :active                ;; #{:active :paused :cancelled}
   :account-id nil                ;; references an entry in :accounts
   :start-date nil
   :end-date nil                  ;; fixed-term subscriptions only; see util/past-end-date?
   :category nil                  ;; freeform, lowercased on save; see :categories
   :auto-renew? true
   :url nil
   :notes nil
   :created-at nil
   :updated-at nil})

(def default-db
  {:subscriptions {}              ;; map of id -> subscription entity
   :accounts      {}              ;; map of id -> account entity
   :categories    {}              ;; map of category name -> {:color ...}
   :ui            {:sort-by :name          ;; #{:name :cost :status}
                    :sort-dir :asc         ;; #{:asc :desc}
                    :status-filter nil     ;; nil (all) or a ::status
                    :category-filter nil   ;; nil (all) or a category name
                    :search ""             ;; substring match against :name
                    :import-error nil}})   ;; set by ::events/import-data on failure

;; -- Spec ------------------------------------------------------------------
;; Fields shared between entities

(s/def ::id string?)
(s/def ::name string?)
(s/def ::notes (s/nilable string?))
(s/def ::created-at (s/nilable string?))
(s/def ::updated-at (s/nilable string?))

;; Account Spec

(s/def ::last-digits string?)

(s/def ::account
  (s/keys :req-un [::id ::name ::last-digits]
          :opt-un [::created-at ::updated-at]))

(s/def ::accounts (s/map-of ::id ::account))

;; Subscription Spec

(s/def ::cost number?)
(s/def ::frequency #{:weekly :monthly :quarterly :yearly :custom})
(s/def ::custom-interval-days (s/nilable pos-int?))
(s/def ::billing-day (s/nilable (s/int-in 1 32)))
(s/def ::billing-month (s/nilable (s/int-in 1 13)))
(s/def ::billing-weekday
  (s/nilable #{:sunday :monday :tuesday :wednesday :thursday :friday :saturday}))
(s/def ::status #{:active :paused :cancelled})
(s/def ::account-id (s/nilable string?))
(s/def ::start-date (s/nilable string?))
(s/def ::end-date (s/nilable string?))
(s/def ::category (s/nilable string?))
(s/def ::auto-renew? boolean?)
(s/def ::url (s/nilable string?))

(defn- billing-info-valid?
  "Monthly/quarterly need :billing-day; yearly needs :billing-day AND
   :billing-month (a day alone doesn't say which month); weekly needs
   :billing-weekday; custom needs :custom-interval-days."
  [{:keys [frequency billing-day billing-month billing-weekday custom-interval-days]}]
  (case frequency
    :weekly (some? billing-weekday)
    :yearly (and (some? billing-day) (some? billing-month))
    :custom (some? custom-interval-days)
    (some? billing-day)))

(s/def ::subscription
  (s/and (s/keys :req-un [::id ::name ::cost ::frequency ::status]
                 :opt-un [::custom-interval-days ::billing-day ::billing-month ::billing-weekday
                          ::account-id ::start-date ::end-date ::category
                          ::auto-renew? ::url ::notes ::created-at ::updated-at])
         billing-info-valid?))

(s/def ::subscriptions (s/map-of ::id ::subscription))

;; Category colors

(s/def ::category-name string?)
(s/def ::color (s/nilable string?))

(s/def ::category-info
  (s/keys :opt-un [::color]))

(s/def ::categories (s/map-of ::category-name ::category-info))

;; Whole app-db

(s/def ::db
  (s/keys :req-un [::subscriptions ::accounts ::categories]))
;; NS: subscription-tracker.db:1 ends here
