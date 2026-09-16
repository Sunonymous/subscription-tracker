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
   :billing-date nil
   :status :active                ;; #{:active :paused :cancelled}
   :account-id nil                ;; references an entry in :accounts
   :start-date nil
   :category nil
   :auto-renew? true
   :url nil
   :color nil
   :notes nil
   :created-at nil
   :updated-at nil})

(def default-db
  {:subscriptions {}              ;; map of id -> subscription entity
   :accounts      {}})            ;; map of id -> account entity

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
(s/def ::billing-date string?)
(s/def ::status #{:active :paused :cancelled})
(s/def ::account-id (s/nilable string?))
(s/def ::start-date (s/nilable string?))
(s/def ::category (s/nilable string?))
(s/def ::auto-renew? boolean?)
(s/def ::url (s/nilable string?))
(s/def ::color (s/nilable string?))

(s/def ::subscription
  (s/keys :req-un [::id ::name ::cost ::frequency ::billing-date ::status]
          :opt-un [::custom-interval-days ::account-id ::start-date ::category
                   ::auto-renew? ::url ::color ::notes ::created-at ::updated-at]))

(s/def ::subscriptions (s/map-of ::id ::subscription))

;; Whole app-db

(s/def ::db
  (s/keys :req-un [::subscriptions ::accounts]))
;; NS: subscription-tracker.db:1 ends here
