;; [[file:../../README.org::*NS: subscription-tracker.events][NS: subscription-tracker.events:1]]
(ns subscription-tracker.events
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [cljs.spec.alpha :as s]
   [cljs.reader :as reader]
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
  "Persists the :subscriptions/:accounts/:categories portion of app-db to
   localStorage after every event."
  (re-frame/after
   (fn [db _]
     (localstorage/store-data! (select-keys db [:subscriptions :accounts :categories])))))

(def mutating-interceptors
  "Applied to every event that makes a significant change to app-db:
   validates the result against spec, then persists it to localStorage."
  [check-spec-interceptor ->local-store])

;; -- Helpers -------------------------------------------------------------

(defn- new-id []
  (str (random-uuid)))

(defn- now-iso []
  (.toISOString (js/Date.)))

(defn- normalize-category
  "Lower-cases :category, if present, so casing variants don't create
   duplicate categories."
  [m]
  (cond-> m (:category m) (update :category str/lower-case)))

(defn- register-category
  "Ensures `category` has an entry in :categories (defaulting to no color
   assigned yet), if it isn't nil and doesn't have one already."
  [db category]
  (if category
    (update db :categories update category #(or % {:color nil}))
    db))

;; -- Events --------------------------------------------------------------

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :local-store)
  check-spec-interceptor
  ->local-store]
 (fn-traced [{:keys [local-store]} _]
            {:db (merge db/default-db local-store)}))

(re-frame/reg-event-db
 ::add-subscription
 mutating-interceptors
 (fn-traced [db [_ subscription]]
            (let [subscription (normalize-category subscription)
                  id (new-id)
                  now (now-iso)
                  entity (merge db/default-subscription subscription
                                 {:id id :created-at now :updated-at now})]
              (-> db
                  (assoc-in [:subscriptions id] entity)
                  (register-category (:category entity))))))

(re-frame/reg-event-db
 ::update-subscription
 mutating-interceptors
 (fn-traced [db [_ id changes]]
            (let [changes (normalize-category changes)]
              (-> db
                  (update-in [:subscriptions id] merge changes {:updated-at (now-iso)})
                  (register-category (:category changes))))))

(re-frame/reg-event-db
 ::add-account
 mutating-interceptors
 (fn-traced [db [_ account]]
            (let [id (new-id)
                  now (now-iso)
                  entity (merge db/default-account account
                                 {:id id :created-at now :updated-at now})]
              (assoc-in db [:accounts id] entity))))

(re-frame/reg-event-db
 ::update-account
 mutating-interceptors
 (fn-traced [db [_ id changes]]
            (update-in db [:accounts id] merge changes {:updated-at (now-iso)})))

(re-frame/reg-event-db
 ::remove-subscription
 mutating-interceptors
 (fn-traced [db [_ id]]
            (update db :subscriptions dissoc id)))

(re-frame/reg-event-db
 ::remove-account
 mutating-interceptors
 (fn-traced [db [_ id]]
            (-> db
                (update :accounts dissoc id)
                (update :subscriptions
                        (fn [subscriptions]
                          (reduce-kv (fn [m sub-id sub]
                                       (assoc m sub-id
                                              (cond-> sub
                                                (= (:account-id sub) id)
                                                (assoc :account-id nil))))
                                     {} subscriptions))))))

(re-frame/reg-event-db
 ::set-category-color
 mutating-interceptors
 (fn-traced [db [_ category-name color]]
            (update-in db [:categories (str/lower-case category-name)] assoc :color color)))

(re-frame/reg-event-db
 ::set-search
 (fn-traced [db [_ text]]
            (assoc-in db [:ui :search] text)))

(re-frame/reg-event-db
 ::set-status-filter
 (fn-traced [db [_ status]]
            (assoc-in db [:ui :status-filter] status)))

(re-frame/reg-event-db
 ::set-category-filter
 (fn-traced [db [_ category]]
            (assoc-in db [:ui :category-filter] category)))

(re-frame/reg-event-db
 ::set-sort-by
 (fn-traced [db [_ sort-key]]
            (assoc-in db [:ui :sort-by] sort-key)))

(re-frame/reg-event-db
 ::toggle-sort-dir
 (fn-traced [db _]
            (update-in db [:ui :sort-dir] #(if (= % :asc) :desc :asc))))

(re-frame/reg-event-db
 ::import-data
 mutating-interceptors
 (fn-traced [db [_ text]]
            (try
              (let [imported (reader/read-string text)
                    candidate (-> db
                                  (update :subscriptions merge (:subscriptions imported))
                                  (update :accounts merge (:accounts imported))
                                  (update :categories merge (:categories imported)))]
                (if (s/valid? ::db/db candidate)
                  (assoc candidate :ui (assoc (:ui db) :import-error nil))
                  (assoc db :ui (assoc (:ui db) :import-error
                                        (str "That file doesn't match the expected data shape: "
                                             (s/explain-str ::db/db candidate))))))
              (catch :default _
                (assoc db :ui (assoc (:ui db) :import-error
                                      "Couldn't read that file as subscription-tracker data."))))))
;; NS: subscription-tracker.events:1 ends here
